package br.com.resenha.Resenha.validation.booking;

import br.com.resenha.Resenha.model.booking.*;
import br.com.resenha.Resenha.model.location.Venue.*;
import br.com.resenha.Resenha.model.location.court.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CourtEligibilityValidatorTest {
    @Test void requiresApprovalMinimumAndMatchingSport() {
        var court = mock(Court.class); var venue = mock(Venue.class);
        when(court.getVenue()).thenReturn(venue); when(court.getType()).thenReturn(CourtType.VOLEI);
        var context = new BookingContext(1L, court,
                new DataCreateBooking(1L, LocalDateTime.now(), CourtType.VOLEI, BookingVisibility.PRIVATE), LocalDateTime.now());
        var validator = new CourtEligibilityValidator();
        when(venue.getStatus()).thenReturn(VenueStatus.PENDING);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> validator.validate(context)).getStatusCode().value());
        when(venue.getStatus()).thenReturn(VenueStatus.APPROVED);
        when(court.getMinimumParticipants()).thenReturn(null);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> validator.validate(context)).getStatusCode().value());
        when(court.getMinimumParticipants()).thenReturn(12);
        assertDoesNotThrow(() -> validator.validate(context));
        when(court.getType()).thenReturn(CourtType.BASQUETE);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> validator.validate(context)).getStatusCode().value());
    }
}
