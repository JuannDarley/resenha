package br.com.resenha.Resenha.model.booking;

import br.com.resenha.Resenha.model.location.court.CourtType;
import java.time.LocalDateTime;

public record DataBooking(Long id, Long courtId, LocalDateTime startsAt, LocalDateTime endsAt,
        LocalDateTime createdAt, CourtType sport, BookingVisibility visibility, BookingStatus status,
        Integer minimumParticipants, java.math.BigDecimal paymentDue, java.math.BigDecimal paymentAmount,
        SimulatedPaymentMethod paymentMethod) {
    public DataBooking(Booking booking) {
        this(booking.getId(), booking.getCourt().getId(), booking.getStartsAt(), booking.getEndsAt(),
                booking.getCreatedAt(), booking.getSport(), booking.getVisibility(), booking.getStatus(),
                booking.getMinimumParticipants(), booking.getPaymentDue(), booking.getPaymentAmount(), booking.getPaymentMethod());
    }
}
