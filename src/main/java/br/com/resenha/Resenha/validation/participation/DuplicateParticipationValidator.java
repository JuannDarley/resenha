package br.com.resenha.Resenha.validation.participation;

import br.com.resenha.Resenha.model.participation.ParticipationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DuplicateParticipationValidator {
    private final ParticipationRepository participations;
    public DuplicateParticipationValidator(ParticipationRepository participations) { this.participations = participations; }
    public void validate(Long matchId, Long userId) {
        if (participations.existsByBookingIdAndUserId(matchId, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Você já possui uma solicitação ou participação nesta partida");
        }
    }
}
