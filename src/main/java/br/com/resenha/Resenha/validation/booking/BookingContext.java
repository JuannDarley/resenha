package br.com.resenha.Resenha.validation.booking;

import br.com.resenha.Resenha.model.booking.DataCreateBooking;
import br.com.resenha.Resenha.model.location.court.Court;
import java.time.LocalDateTime;

public record BookingContext(Long creatorId, Court court, DataCreateBooking request, LocalDateTime now) {}
