package br.com.resenha.Resenha.validation.booking;

import br.com.resenha.Resenha.model.booking.BookingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;

/** Prevents manual availability changes from overriding an active reservation. */
@Component
public class ReservedSlotValidator {
    private final BookingRepository bookings;
    public ReservedSlotValidator(BookingRepository bookings) { this.bookings = bookings; }
    public void validate(Long courtId, LocalDateTime start) {
        if (!bookings.findOverlapping(courtId, start, start.plusHours(1)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Horário já reservado; bloqueio manual não altera reservas");
        }
    }
}
