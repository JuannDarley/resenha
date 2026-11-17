package br.com.resenha.Resenha.model.location.availability;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record DataSlotUpdate(@NotNull LocalDateTime startsAt, @NotNull Boolean available) {}
