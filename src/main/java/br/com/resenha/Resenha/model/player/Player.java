package br.com.resenha.Resenha.model.player;


import br.com.resenha.Resenha.model.address.Address;
import br.com.resenha.Resenha.model.address.DataRegisterPlayer;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(name = "player")
@Entity(name = "Player")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private Long age;
    private String email;

    @Embedded
    private Address address;


    public Player(DataRegisterPlayer data) {
        this.id = data.id();
        this.name = data.name();
        this.age = data.age();
        this.email = data.email();
        this.address = new Address(data.address());
    }
}
