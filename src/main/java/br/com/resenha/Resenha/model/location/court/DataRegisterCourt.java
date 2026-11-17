package br.com.resenha.Resenha.model.location.court;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DataRegisterCourt(
        @NotBlank
        String name,
        @NotNull
        CourtType type,
        @NotNull
        Long venueId,
        @NotNull @Positive
        Integer minimumParticipants
) {}