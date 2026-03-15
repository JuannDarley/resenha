package br.com.resenha.Resenha.model.player;

public record DataListPlayer(
        Long id,
        String name,
        Long age,
        String email
        ) {

    public DataListPlayer (Player player){
        this(
                player.getId(),
                player.getName(),
                player.getAge(),
                player.getEmail()
        );
    }
}
