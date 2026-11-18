package br.com.resenha.Resenha.validation.participation;

import br.com.resenha.Resenha.model.booking.*;
import br.com.resenha.Resenha.service.InvitationCodes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class InvitationValidator {
    private final InvitationCodes codes;
    public InvitationValidator(InvitationCodes codes) { this.codes = codes; }
    public void validate(Booking booking, String code) {
        if (code != null && (code.isBlank() || booking.getVisibility() != BookingVisibility.PRIVATE
                || !codes.matches(code, booking.getInvitationHash()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Convite inválido ou revogado");
        }
    }
}
