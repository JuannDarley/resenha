package br.com.resenha.Resenha.model.location.court;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DataRegisterCourt(
        @NotBlank
        String name,
        @NotNull
        CourtType type,
        @NotNull
        Long venueId
) {}