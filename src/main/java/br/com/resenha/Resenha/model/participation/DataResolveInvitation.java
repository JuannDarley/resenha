package br.com.resenha.Resenha.model.participation;

import jakarta.validation.constraints.*;

public record DataResolveInvitation(@NotBlank @Size(max = 128) String code) {}
