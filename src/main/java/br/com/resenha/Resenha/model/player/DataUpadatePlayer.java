package br.com.resenha.Resenha.model.player;

import br.com.resenha.Resenha.model.address.DataAddress;
import jakarta.validation.constraints.NotNull;

public record DataUpadatePlayer(

        String name,
        Long age,
        String email,
        DataAddress address
) {
}
