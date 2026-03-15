package br.com.resenha.Resenha.model.location.Venue;

public record DataListVenue(
        Long id,
        String name,
        String openingTime,
        String closingTime,
        Integer courtCount
) {
    public DataListVenue(Venue venue) {
        this(venue.getId(), venue.getName(), venue.getOpeningTime(),
                venue.getClosingTime(), venue.getCourtCount());
    }
}