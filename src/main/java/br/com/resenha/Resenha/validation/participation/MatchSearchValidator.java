package br.com.resenha.Resenha.validation.participation;

import java.time.LocalTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MatchSearchValidator {
    public void validate(Long venueId, LocalTime time, String city) {
        if ((venueId != null && venueId <= 0) || (time != null && (time.getSecond() != 0 || time.getNano() != 0))
                || (city != null && city.length() > 100)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro de local ou horário inválido; informe horário em HH:mm");
        }
    }
}
