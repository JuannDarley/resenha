package br.com.resenha.Resenha.model.location.Venue;

import br.com.resenha.Resenha.model.address.DataAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DataRegisterVenue(
        @NotBlank
        String name,
        @NotBlank
        String description,
        @NotBlank
        String openingTime,
        @NotBlank
        String closingTime,
        @NotNull
        Integer courtCount,
        @NotNull
        @Valid
        DataAddress address
) {}