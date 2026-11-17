package br.com.resenha.Resenha.validation.booking;

import br.com.resenha.Resenha.model.booking.BookingRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** RF08: counts creations today, including cancelled games, regardless of their scheduled dates. */
@Component
@Order(30)
public class DailyCreationLimitValidator implements BookingValidator {
    private final BookingRepository bookings;
    public DailyCreationLimitValidator(BookingRepository bookings) { this.bookings = bookings; }
    @Override
    public void validate(BookingContext context) {
        var from = context.now().toLocalDate().atStartOfDay();
        if (bookings.countByCreatorIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                context.creatorId(), from, from.plusDays(1)) >= 3) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Limite de 3 partidas criadas por dia atingido, incluindo canceladas");
        }
    }
}
