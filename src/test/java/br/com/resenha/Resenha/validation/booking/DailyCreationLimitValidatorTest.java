package br.com.resenha.Resenha.validation.booking;

import br.com.resenha.Resenha.model.booking.BookingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DailyCreationLimitValidatorTest {
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final DailyCreationLimitValidator validator = new DailyCreationLimitValidator(bookings);
    @Test void countsCreationDayUsingHalfOpenRangeAndRejectsAtThree() {
        var now = LocalDateTime.parse("2026-11-17T23:59:59");
        var context = new BookingContext(42L, null, null, now);
        var from = now.toLocalDate().atStartOfDay();
        when(bookings.countByCreatorIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(42L, from, from.plusDays(1)))
                .thenReturn(2L, 3L);
        assertDoesNotThrow(() -> validator.validate(context));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> validator.validate(context)).getStatusCode().value());
        verify(bookings, times(2)).countByCreatorIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(42L, from, from.plusDays(1));
    }
}
