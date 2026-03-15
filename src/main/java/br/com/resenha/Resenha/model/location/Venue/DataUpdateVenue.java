package br.com.resenha.Resenha.model.location.Venue;

import br.com.resenha.Resenha.model.address.DataAddress;
import jakarta.validation.constraints.NotNull;

public record DataUpdateVenue(
        @NotNull
        Long id,
        String name,
        String description,
        String openingTime,
        String closingTime,
        Integer courtCount,
        DataAddress address
) {}