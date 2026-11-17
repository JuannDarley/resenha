package br.com.resenha.Resenha.validation.booking;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalTime;

/** RN01: eight hours only for same-day games; creation after 18:00 cannot target today. */
@Component
@Order(10)
public class AdvanceNoticeValidator implements BookingValidator {
    @Override
    public void validate(BookingContext context) {
        var start = context.request().startsAt();
        var now = context.now();
        if (!start.isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O agendamento deve ser no futuro");
        }
        if (start.toLocalDate().equals(now.toLocalDate())) {
            if (now.toLocalTime().isAfter(LocalTime.of(18, 0))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Após as 18h não é permitido agendar para o mesmo dia");
            }
            if (start.isBefore(now.plusHours(8))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agendamentos para o mesmo dia exigem ao menos 8 horas de antecedência");
            }
        }
    }
}
