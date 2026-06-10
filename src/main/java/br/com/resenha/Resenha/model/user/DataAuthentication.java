package br.com.resenha.Resenha.model.user;

import jakarta.validation.constraints.NotEmpty;

public record DataAuthentication(
        @NotEmpty (message = "Email is mandatory")
        String email,
        @NotEmpty (message = "Password is mandatory")
        String password
) {
}
