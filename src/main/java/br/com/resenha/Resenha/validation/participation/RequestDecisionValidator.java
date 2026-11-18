package br.com.resenha.Resenha.validation.participation;

import br.com.resenha.Resenha.model.participation.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RequestDecisionValidator {
    public void validate(Participation participation) {
        if (participation.getStatus() != ParticipationStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A solicitação não está aguardando uma decisão");
        }
    }
}
