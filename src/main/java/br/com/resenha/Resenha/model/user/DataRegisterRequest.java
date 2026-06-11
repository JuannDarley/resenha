package br.com.resenha.Resenha.model.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record DataRegisterRequest(

        @NotEmpty (message = "Email is mandatory")
        @Email
        String email,

        @NotEmpty (message = "Password is mandatory")
        String password,

        @NotNull(message = "This roles is PLAYER, OWNER e ADMIN")
        Role role


) {
}
