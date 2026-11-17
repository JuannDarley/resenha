package br.com.resenha.Resenha.model.location.court;

public record DataDetailCourt(
        Long id,
        String name,
        CourtType type,
        Integer minimumParticipants,
        Long venueId
) {
    public DataDetailCourt(Court court) {
        this(court.getId(), court.getName(), court.getType(), court.getMinimumParticipants(), court.getVenue().getId());
    }
}