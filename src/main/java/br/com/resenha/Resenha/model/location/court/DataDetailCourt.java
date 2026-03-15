package br.com.resenha.Resenha.model.location.court;

public record DataDetailCourt(
        Long id,
        String name,
        CourtType type
) {
    public DataDetailCourt(Court court) {
        this(court.getId(), court.getName(), court.getType());
    }
}