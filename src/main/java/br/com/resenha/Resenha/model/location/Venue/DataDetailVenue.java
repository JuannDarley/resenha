package br.com.resenha.Resenha.model.location.Venue;

import br.com.resenha.Resenha.model.address.Address;
import br.com.resenha.Resenha.model.location.court.Court;
import br.com.resenha.Resenha.model.location.court.DataDetailCourt;

import java.util.List;

public record DataDetailVenue(
        Long id,
        String name,
        String description,
        String openingTime,
        String closingTime,
        Integer courtCount,
        Address address,
        List<DataDetailCourt> courts
) {
    public DataDetailVenue(Venue venue) {
        this(venue.getId(),
                venue.getName(),
                venue.getDescription(),
                venue.getOpeningTime(),
                venue.getClosingTime(),
                venue.getCourtCount(),
                venue.getAddress(),
                venue.getCourts().stream()
                        .map(DataDetailCourt::new)
                        .toList());
    }
}