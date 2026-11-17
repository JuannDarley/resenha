package br.com.resenha.Resenha.service;

import br.com.resenha.Resenha.model.location.Venue.*;
import br.com.resenha.Resenha.model.booking.BookingRepository;
import br.com.resenha.Resenha.validation.booking.ReservedSlotValidator;
import br.com.resenha.Resenha.model.location.court.*;
import br.com.resenha.Resenha.model.location.availability.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service
public class CourtAvailabilityService {
    private final CourtRepository courts;
    private final CourtSlotBlockRepository blocks;
    private final Clock clock;
    private final BookingRepository bookings;
    private final ReservedSlotValidator reservedSlots;

    public CourtAvailabilityService(CourtRepository courts, CourtSlotBlockRepository blocks, Clock clock, BookingRepository bookings, ReservedSlotValidator reservedSlots) {
        this.courts = courts;
        this.blocks = blocks;
        this.clock = clock;
        this.bookings = bookings;
        this.reservedSlots = reservedSlots;
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<DataAvailableCourt> availableCourts(org.springframework.data.domain.Pageable pageable) {
        return courts.findAllByVenueStatusAndMinimumParticipantsIsNotNull(VenueStatus.APPROVED, pageable)
                .map(DataAvailableCourt::new);
    }

    @Transactional(readOnly = true)
    public List<DataSlot> ownerSlots(Long id, Long ownerId, LocalDate date) {
        return slots(ownedCourt(id, ownerId), date);
    }

    @Transactional(readOnly = true)
    public List<DataSlot> playerSlots(Long id, LocalDate date) {
        Court court = courts.findById(id).filter(c -> c.getVenue().getStatus() == VenueStatus.APPROVED
                && c.getMinimumParticipants() != null).orElseThrow(() -> notFound());
        return slots(court, date);
    }

    @Transactional
    public void updateSlot(Long id, Long ownerId, DataSlotUpdate data) {
        Court court = courts.findByIdForUpdate(id).filter(c -> c.getVenue().getOwner() != null
                && c.getVenue().getOwner().getId().equals(ownerId)).orElseThrow(() -> notFound());
        reservedSlots.validate(id, data.startsAt());
        // Overnight slots after midnight belong to the preceding opening date.
        LocalDate date = data.startsAt().toLocalDate();
        LocalTime opening = OperatingHours.parse(court.getVenue().getOpeningTime());
        LocalTime closing = OperatingHours.parse(court.getVenue().getClosingTime());
        if (closing.isBefore(opening) && data.startsAt().toLocalTime().isBefore(closing)) {
            date = date.minusDays(1);
        }
        if (slots(court, date).stream().noneMatch(slot -> slot.startsAt().equals(data.startsAt()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Horário fora dos slots disponíveis da quadra");
        }
        if (data.available()) {
            blocks.unblock(id, data.startsAt().minusHours(1), data.startsAt().plusHours(1));
        } else {
            blocks.block(id, data.startsAt());
        }
    }

    private Court ownedCourt(Long id, Long ownerId) {
        return courts.findById(id).filter(c -> c.getVenue().getOwner() != null
                && c.getVenue().getOwner().getId().equals(ownerId)).orElseThrow(() -> notFound());
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Quadra não encontrada ou indisponível");
    }

    private List<DataSlot> slots(Court court, LocalDate date) {
        if (date.isBefore(LocalDate.now(clock).minusDays(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data de disponibilidade já passou");
        }
        if (court.getMinimumParticipants() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Configure o mínimo de participantes da quadra");
        }
        Venue venue = court.getVenue();
        OperatingHours.validate(venue.getOpeningTime(), venue.getClosingTime());
        LocalDateTime start = date.atTime(OperatingHours.parse(venue.getOpeningTime()));
        LocalDateTime end = date.atTime(OperatingHours.parse(venue.getClosingTime()));
        if (end.isBefore(start)) end = end.plusDays(1);
        var blocked = blocks.findAllByCourtIdAndStartsAtGreaterThanAndStartsAtLessThan(
                court.getId(), start.minusHours(1), end);
        var reserved = bookings.findOverlapping(court.getId(), start, end);
        List<DataSlot> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(clock);
        for (LocalDateTime cursor = start; !cursor.plusHours(1).isAfter(end); cursor = cursor.plusHours(1)) {
            if (cursor.isBefore(now)) continue;
            LocalDateTime slotStart = cursor, slotEnd = cursor.plusHours(1);
            boolean available = blocked.stream().noneMatch(b -> b.getStartsAt().isBefore(slotEnd)
                    && b.getStartsAt().plusHours(1).isAfter(slotStart));
            boolean occupied = reserved.stream().anyMatch(b -> b.getStartsAt().isBefore(slotEnd)
                    && b.getEndsAt().isAfter(slotStart));
            SlotStatus status = occupied ? SlotStatus.RESERVED : available ? SlotStatus.AVAILABLE : SlotStatus.BLOCKED;
            result.add(new DataSlot(slotStart, slotEnd, available && !occupied, status));
        }
        return result;
    }
}
