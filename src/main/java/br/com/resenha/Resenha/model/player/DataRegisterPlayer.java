package br.com.resenha.Resenha.model.player;

import br.com.resenha.Resenha.model.address.DataAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DataRegisterPlayer(

        @NotBlank
        String name,

        @NotNull
        Long age,

        @NotBlank
        @Email
        String email,

        @NotNull
        @Valid
        DataAddress address
) {
}
