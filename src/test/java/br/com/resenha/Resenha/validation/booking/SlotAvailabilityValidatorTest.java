package br.com.resenha.Resenha.validation.booking;

import br.com.resenha.Resenha.model.booking.*;
import br.com.resenha.Resenha.model.location.Venue.*;
import br.com.resenha.Resenha.model.location.court.*;
import br.com.resenha.Resenha.model.location.availability.DataSlot;
import br.com.resenha.Resenha.service.CourtAvailabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SlotAvailabilityValidatorTest {
    @Test void usesPreviousOpeningDateForOvernightSlotsAndRejectsBlockedOrOffGrid() {
        var availability = mock(CourtAvailabilityService.class);
        var court = mock(Court.class); var venue = mock(Venue.class);
        when(court.getId()).thenReturn(1L); when(court.getVenue()).thenReturn(venue);
        when(venue.getOpeningTime()).thenReturn("22:00"); when(venue.getClosingTime()).thenReturn("02:00");
        var start = LocalDateTime.parse("2026-11-18T00:00:00");
        var context = new BookingContext(1L, court, new DataCreateBooking(1L, start, CourtType.SOCIETY, BookingVisibility.PUBLIC), start.minusDays(1));
        when(availability.playerSlots(1L, start.toLocalDate().minusDays(1))).thenReturn(
                List.of(new DataSlot(start, start.plusHours(1), true)),
                List.of(new DataSlot(start, start.plusHours(1), false)), List.of());
        var validator = new SlotAvailabilityValidator(availability);
        assertDoesNotThrow(() -> validator.validate(context));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> validator.validate(context)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> validator.validate(context)).getStatusCode().value());
    }
}
