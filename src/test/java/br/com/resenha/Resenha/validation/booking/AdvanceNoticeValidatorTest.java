package br.com.resenha.Resenha.validation.booking;

import br.com.resenha.Resenha.model.booking.*;
import br.com.resenha.Resenha.model.location.court.CourtType;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class AdvanceNoticeValidatorTest {
    private final AdvanceNoticeValidator validator = new AdvanceNoticeValidator();
    private void validate(String now, String start) {
        validator.validate(new BookingContext(1L, null,
                new DataCreateBooking(1L, LocalDateTime.parse(start), CourtType.SOCIETY, BookingVisibility.PUBLIC),
                LocalDateTime.parse(now)));
    }
    @Test void acceptsExactlyEightHoursAndRejectsOneSecondLess() {
        assertDoesNotThrow(() -> validate("2026-11-17T08:00:00", "2026-11-17T16:00:00"));
        assertThrows(ResponseStatusException.class, () -> validate("2026-11-17T08:00:01", "2026-11-17T16:00:00"));
    }
    @Test void tomorrowRequiresNoEightHourNoticeEvenAfter18() {
        assertDoesNotThrow(() -> validate("2026-11-17T23:59:00", "2026-11-18T00:00:00"));
    }
    @Test void rejectsSameDayAfter18WithExplicitCutoffReason() {
        var error = assertThrows(ResponseStatusException.class,
                () -> validate("2026-11-17T18:00:01", "2026-11-17T23:00:00"));
        assertTrue(error.getReason().contains("Após as 18h"));
        var exactly18 = assertThrows(ResponseStatusException.class,
                () -> validate("2026-11-17T18:00:00", "2026-11-17T23:00:00"));
        assertTrue(exactly18.getReason().contains("8 horas"));
    }
    @Test void rejectsPastDaysPastTimesAndNow() {
        for (String start : new String[]{"2026-11-16T20:00:00", "2026-11-17T07:59:00", "2026-11-17T08:00:00"}) {
            assertThrows(ResponseStatusException.class, () -> validate("2026-11-17T08:00:00", start));
        }
    }
}
