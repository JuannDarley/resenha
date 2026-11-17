package br.com.resenha.Resenha.model.location.availability;

import br.com.resenha.Resenha.model.location.court.*;

public record DataAvailableCourt(Long id, String name, Long venueId, String venueName,
        CourtType sport, Integer minimumParticipants) {
    public DataAvailableCourt(Court court) {
        this(court.getId(), court.getName(), court.getVenue().getId(), court.getVenue().getName(),
                court.getType(), court.getMinimumParticipants());
    }
}
