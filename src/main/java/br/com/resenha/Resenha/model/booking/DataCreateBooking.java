package br.com.resenha.Resenha.model.booking;

import br.com.resenha.Resenha.model.location.court.CourtType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public record DataCreateBooking(
        @NotNull @Positive Long courtId,
        @NotNull LocalDateTime startsAt,
        @NotNull CourtType sport,
        @NotNull BookingVisibility visibility) {}
