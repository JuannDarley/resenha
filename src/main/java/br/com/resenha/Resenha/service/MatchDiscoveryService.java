package br.com.resenha.Resenha.service;

import br.com.resenha.Resenha.model.booking.*;
import br.com.resenha.Resenha.model.participation.*;
import br.com.resenha.Resenha.model.location.Venue.VenueStatus;
import br.com.resenha.Resenha.model.location.court.CourtType;
import br.com.resenha.Resenha.validation.participation.MatchSearchValidator;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import jakarta.persistence.criteria.Predicate;

@Service
public class MatchDiscoveryService {
    private final BookingRepository bookings;
    private final ParticipationRepository participations;
    private final MatchSearchValidator filters;
    private final Clock clock;
    public MatchDiscoveryService(BookingRepository bookings, ParticipationRepository participations,
            MatchSearchValidator filters, Clock clock) {
        this.bookings = bookings; this.participations = participations; this.filters = filters; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<DataMatch> list(Long caller, LocalDate date, LocalTime time, Long venueId, String city,
            CourtType sport, Pageable pageable) {
        filters.validate(venueId, time, city);
        var now = LocalDateTime.now(clock);
        Specification<Booking> search = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("status"), BookingStatus.OPEN));
            predicates.add(cb.greaterThan(root.get("startsAt"), now));
            predicates.add(cb.equal(root.get("court").get("venue").get("status"), VenueStatus.APPROVED));
            if (date != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startsAt"), date.atStartOfDay()));
                predicates.add(cb.lessThan(root.get("startsAt"), date.plusDays(1).atStartOfDay()));
            }
            if (time != null) predicates.add(cb.equal(cb.function("to_char", String.class, root.get("startsAt"),
                    cb.literal("HH24:MI")), time.format(DateTimeFormatter.ofPattern("HH:mm"))));
            if (venueId != null) predicates.add(cb.equal(root.get("court").get("venue").get("id"), venueId));
            if (sport != null) predicates.add(cb.equal(root.get("sport"), sport));
            if (city != null && !city.isBlank()) predicates.add(cb.equal(cb.lower(root.get("court").get("venue")
                    .get("address").get("city")), city.strip().toLowerCase(java.util.Locale.ROOT)));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return bookings.findAll(search, pageable).map(b -> describe(b, caller, now));
    }

    @Transactional(readOnly = true)
    public DataMatch detail(Long id, Long caller) {
        return describe(visible(id), caller, LocalDateTime.now(clock));
    }

    public Booking visible(Long id) {
        return bookings.findById(id).filter(b -> b.getStatus() == BookingStatus.OPEN
                && b.getStartsAt().isAfter(LocalDateTime.now(clock))
                && b.getCourt().getVenue().getStatus() == VenueStatus.APPROVED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partida não encontrada"));
    }

    public DataMatch describe(Booking booking, Long caller, LocalDateTime now) {
        return new DataMatch(booking, participations.countByBookingIdAndStatus(booking.getId(), ParticipationStatus.CONFIRMED), caller, now,
                participations.findByBookingIdAndUserId(booking.getId(), caller).map(Participation::getStatus).orElse(null));
    }
}
