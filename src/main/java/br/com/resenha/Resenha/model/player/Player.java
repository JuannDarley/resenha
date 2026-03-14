package br.com.resenha.Resenha.model.player;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class Player {
    private Long id;
    private String name;
    private Integer age;
    private String email;
    private String address;


    public Player(Player dados) {
        this.id = dados.id;
        this.name = dados.name;
        this.age = dados.age;
        this.email = dados.email;
        this.address = dados.address;
    }
}
