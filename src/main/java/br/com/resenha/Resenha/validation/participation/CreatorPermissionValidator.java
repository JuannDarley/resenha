package br.com.resenha.Resenha.validation.participation;

import br.com.resenha.Resenha.model.booking.Booking;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CreatorPermissionValidator {
    public void validate(Booking booking, Long userId) {
        if (!booking.getCreator().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente o criador pode gerenciar esta partida");
        }
    }
}
