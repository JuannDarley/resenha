package br.com.resenha.Resenha.service;

import br.com.resenha.Resenha.model.location.Venue.*;
import br.com.resenha.Resenha.model.location.court.*;
import br.com.resenha.Resenha.model.location.availability.*;
import br.com.resenha.Resenha.model.address.DataAddress;
import br.com.resenha.Resenha.model.user.User;
import org.junit.jupiter.api.Test;
import br.com.resenha.Resenha.model.booking.BookingRepository;
import br.com.resenha.Resenha.validation.booking.ReservedSlotValidator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CourtAvailabilityServiceTest {
    private final CourtRepository courts = mock(CourtRepository.class);
    private final CourtSlotBlockRepository blocks = mock(CourtSlotBlockRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final ReservedSlotValidator reservedSlots = new ReservedSlotValidator(bookings);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-17T15:30:00Z"), ZoneId.of("America/Sao_Paulo"));
    private final CourtAvailabilityService service = new CourtAvailabilityService(courts, blocks, clock, bookings, reservedSlots);
    private final LocalDate tomorrow = LocalDate.of(2026, 11, 18);

    private Court court(String opening, String closing) {
        User owner = new User(); owner.setId(1L);
        Venue venue = new Venue(new DataRegisterVenue("Arena", "Local", opening, closing, 1,
                new DataAddress("Rua", "Centro", "12345678", "Cidade", "SP", null, "1")), owner);
        Court court = new Court(new DataRegisterCourt("Quadra", CourtType.VOLEI, 1L, 12), venue);
        ReflectionTestUtils.setField(court, "id", 1L);
        when(courts.findById(1L)).thenReturn(Optional.of(court));
        when(courts.findByIdForUpdate(1L)).thenReturn(Optional.of(court));
        when(blocks.findAllByCourtIdAndStartsAtGreaterThanAndStartsAtLessThan(anyLong(), any(), any()))
                .thenReturn(List.of());
        return court;
    }

    @Test void buildsOnlyCompleteHourlySlotsAnchoredAtOpeningTime() {
        court("08:30", "11:45");
        var slots = service.ownerSlots(1L, 1L, tomorrow);
        assertEquals(3, slots.size());
        assertEquals(tomorrow.atTime(8, 30), slots.get(0).startsAt());
        assertEquals(tomorrow.atTime(11, 30), slots.get(2).endsAt());
        assertTrue(slots.stream().allMatch(s -> Duration.between(s.startsAt(), s.endsAt()).toMinutes() == 60 && s.available()));
    }

    @Test void handlesOvernightClosingAndOnlyFutureSlotsInSaoPaulo() {
        court("22:00", "02:00");
        var slots = service.ownerSlots(1L, 1L, tomorrow);
        assertEquals(4, slots.size());
        assertEquals(tomorrow.plusDays(1).atTime(2, 0), slots.get(3).endsAt());
        court("08:00", "15:00");
        slots = service.ownerSlots(1L, 1L, LocalDate.now(clock));
        assertEquals(2, slots.size());
        assertEquals(LocalDate.now(clock).atTime(13, 0), slots.get(0).startsAt());
    }

    @Test void overlappingBlockStillAppliesAfterOpeningTimeChanges() {
        court("08:30", "11:30");
        CourtSlotBlock block = new CourtSlotBlock();
        ReflectionTestUtils.setField(block, "startsAt", tomorrow.atTime(9, 0));
        when(blocks.findAllByCourtIdAndStartsAtGreaterThanAndStartsAtLessThan(anyLong(), any(), any()))
                .thenReturn(List.of(block));
        var slots = service.ownerSlots(1L, 1L, tomorrow);
        assertFalse(slots.get(0).available());
        assertFalse(slots.get(1).available());
        assertTrue(slots.get(2).available());
    }

    @Test void rejectsOffGridSecondsPastSlotsAndForeignOwnersWithoutWriting() {
        court("08:00", "22:00");
        for (LocalDateTime start : List.of(tomorrow.atTime(8, 30), tomorrow.atTime(8, 0, 1),
                tomorrow.atTime(22, 0), LocalDate.now(clock).atTime(9, 0))) {
            assertThrows(ResponseStatusException.class,
                    () -> service.updateSlot(1L, 1L, new DataSlotUpdate(start, false)));
        }
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.ownerSlots(1L, 2L, tomorrow)).getStatusCode().value());
        verify(blocks, never()).block(anyLong(), any());
        verify(blocks, never()).unblock(anyLong(), any(), any());
    }

    @Test void playerRequiresApprovedAndConfiguredCourt() {
        Court court = court("08:00", "22:00");
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.playerSlots(1L, tomorrow)).getStatusCode().value());
        court.getVenue().approve();
        assertEquals(14, service.playerSlots(1L, tomorrow).size());
        ReflectionTestUtils.setField(court, "minimumParticipants", null);
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.playerSlots(1L, tomorrow)).getStatusCode().value());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.ownerSlots(1L, 1L, tomorrow)).getStatusCode().value());
    }

    @Test void overnightSlotCanBeBlockedAndUnblockedUsingItsActualDate() {
        court("22:00", "02:00");
        LocalDateTime start = tomorrow.plusDays(1).atStartOfDay();
        service.updateSlot(1L, 1L, new DataSlotUpdate(start, false));
        verify(blocks).block(1L, start);
        service.updateSlot(1L, 1L, new DataSlotUpdate(start, true));
        verify(blocks).unblock(1L, start.minusHours(1), start.plusHours(1));
    }

    @Test void rejectsInvalidAndTooShortOperatingHours() {
        for (String[] times : List.of(new String[]{"25:00", "22:00"}, new String[]{"8:00", "22:00"},
                new String[]{"08:00", "08:00"}, new String[]{"08:00", "08:59"}, new String[]{"", "10:00"})) {
            assertThrows(ResponseStatusException.class, () -> OperatingHours.validate(times[0], times[1]));
        }
        assertDoesNotThrow(() -> OperatingHours.validate("23:30", "00:30"));
    }
    @Test void previousOpeningDateExposesOnlyRemainingOvernightSlots() {
        court("22:00", "02:00");
        Clock afterMidnight = Clock.fixed(Instant.parse("2026-11-17T03:30:00Z"), ZoneId.of("America/Sao_Paulo"));
        var midnightService = new CourtAvailabilityService(courts, blocks, afterMidnight, bookings, reservedSlots);
        var slots = midnightService.ownerSlots(1L, 1L, LocalDate.of(2026, 11, 16));
        assertEquals(1, slots.size());
        assertEquals(LocalDate.of(2026, 11, 17).atTime(1, 0), slots.get(0).startsAt());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> midnightService.ownerSlots(1L, 1L, LocalDate.of(2026, 11, 15))).getStatusCode().value());
    }

    @Test void adjacentBlocksDoNotHideNonOverlappingSlots() {
        court("08:00", "11:00");
        CourtSlotBlock block = new CourtSlotBlock();
        ReflectionTestUtils.setField(block, "startsAt", tomorrow.atTime(9, 0));
        when(blocks.findAllByCourtIdAndStartsAtGreaterThanAndStartsAtLessThan(anyLong(), any(), any()))
                .thenReturn(List.of(block));
        var slots = service.ownerSlots(1L, 1L, tomorrow);
        assertTrue(slots.get(0).available());
        assertFalse(slots.get(1).available());
        assertTrue(slots.get(2).available());
    }

}
