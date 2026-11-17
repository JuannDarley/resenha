package br.com.resenha.Resenha.validation.booking;

import br.com.resenha.Resenha.model.location.Venue.OperatingHours;
import br.com.resenha.Resenha.service.CourtAvailabilityService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Component
@Order(40)
public class SlotAvailabilityValidator implements BookingValidator {
    private final CourtAvailabilityService availability;
    public SlotAvailabilityValidator(CourtAvailabilityService availability) { this.availability = availability; }
    @Override
    public void validate(BookingContext context) {
        var start = context.request().startsAt();
        var venue = context.court().getVenue();
        var opening = OperatingHours.parse(venue.getOpeningTime());
        var closing = OperatingHours.parse(venue.getClosingTime());
        var date = start.toLocalDate();
        if (closing.isBefore(opening) && start.toLocalTime().isBefore(closing)) date = date.minusDays(1);
        var slot = availability.playerSlots(context.court().getId(), date).stream()
                .filter(s -> s.startsAt().equals(start)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Horário fora da grade de slots de 1 hora da quadra"));
        if (!slot.available()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Horário bloqueado ou já reservado");
        }
    }
}
