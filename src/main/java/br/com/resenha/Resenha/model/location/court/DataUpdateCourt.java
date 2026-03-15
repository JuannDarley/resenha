package br.com.resenha.Resenha.model.location.court;

import jakarta.validation.constraints.NotNull;

public record DataUpdateCourt(
        @NotNull
        Long id,
        String name,
        CourtType type
) {}