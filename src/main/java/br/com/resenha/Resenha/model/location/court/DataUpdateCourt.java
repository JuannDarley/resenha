package br.com.resenha.Resenha.model.location.court;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DataUpdateCourt(
        @NotNull
        Long id,
        String name,
        CourtType type,
        @Positive
        Integer minimumParticipants
) {}