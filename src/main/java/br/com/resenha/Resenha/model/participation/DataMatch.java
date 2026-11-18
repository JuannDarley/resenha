package br.com.resenha.Resenha.model.participation;

import br.com.resenha.Resenha.model.booking.*;
import br.com.resenha.Resenha.model.location.court.CourtType;
import java.time.LocalDateTime;

/** Safe discovery DTO for both public and private matches. Never serializes users or requests. */
public record DataMatch(Long id, Long courtId, String courtName, Long venueId, String venueName,
        String city, CourtType sport, LocalDateTime startsAt, LocalDateTime endsAt,
        BookingVisibility visibility, BookingStatus status, Integer minimumParticipants,
        long participantCount, boolean createdByMe, boolean joinable, ParticipationStatus myParticipationStatus) {
    public DataMatch(Booking booking, long count, Long callerId, LocalDateTime now, ParticipationStatus myStatus) {
        this(booking.getId(), booking.getCourt().getId(), booking.getCourt().getName(),
                booking.getCourt().getVenue().getId(), booking.getCourt().getVenue().getName(),
                booking.getCourt().getVenue().getAddress().getCity(), booking.getSport(), booking.getStartsAt(),
                booking.getEndsAt(), booking.getVisibility(), booking.getStatus(), booking.getMinimumParticipants(),
                count, booking.getCreator().getId().equals(callerId), now.isBefore(booking.getStartsAt().minusHours(1))
                        && booking.getStatus() == BookingStatus.OPEN, myStatus);
    }
}
