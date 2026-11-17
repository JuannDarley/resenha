package br.com.resenha.Resenha.model.location.Venue;

import java.time.LocalTime;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class OperatingHours {
    private OperatingHours() {}

    public static void validate(String opening, String closing) {
        LocalTime start = parse(opening), end = parse(closing);
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < 0) minutes += 24 * 60;
        if (minutes < 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Funcionamento deve permitir pelo menos um slot de 1 hora; abertura e fechamento devem ser diferentes");
        }
    }

    public static LocalTime parse(String value) {
        if (value == null || !value.matches("([01]\\d|2[0-3]):[0-5]\\d")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Horário deve estar no formato HH:mm (00:00 a 23:59)");
        }
        return LocalTime.parse(value);
    }
}
