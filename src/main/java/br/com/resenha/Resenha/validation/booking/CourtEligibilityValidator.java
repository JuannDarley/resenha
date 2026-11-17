package br.com.resenha.Resenha.validation.booking;

import br.com.resenha.Resenha.model.location.Venue.VenueStatus;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Component
@Order(20)
public class CourtEligibilityValidator implements BookingValidator {
    @Override
    public void validate(BookingContext context) {
        var court = context.court();
        if (court.getVenue().getStatus() != VenueStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O estabelecimento precisa estar aprovado");
        }
        if (court.getMinimumParticipants() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A quadra ainda não tem mínimo de participantes configurado");
        }
        if (court.getType() != context.request().sport()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A modalidade deve corresponder à configuração da quadra");
        }
    }
}
