package br.com.resenha.Resenha.validation.participation;

import br.com.resenha.Resenha.model.booking.*;
import br.com.resenha.Resenha.model.location.Venue.VenueStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;

@Component
public class ParticipationWindowValidator {
    public void validate(Booking booking, LocalDateTime now) {
        if (booking.getStatus() != BookingStatus.OPEN
                || booking.getCourt().getVenue().getStatus() != VenueStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Partida não está aberta em estabelecimento aprovado");
        }
        if (!now.isBefore(booking.getStartsAt().minusHours(1))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Entradas e decisões estão encerradas a partir de 1 hora antes da partida");
        }
    }
}
