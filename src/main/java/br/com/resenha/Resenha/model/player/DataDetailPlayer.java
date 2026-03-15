package br.com.resenha.Resenha.model.player;

import br.com.resenha.Resenha.model.address.Address;

public record DataDetailPlayer(
        Long id,
        String name,
        Long age,
        String email,
        Address address
) {

    public DataDetailPlayer(Player player) {
        this(player.getId(),
                player.getName(),
                player.getAge(),
                player.getEmail(),
                player.getAddress());
    }
}
