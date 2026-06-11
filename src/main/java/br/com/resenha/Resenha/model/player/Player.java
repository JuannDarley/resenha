package br.com.resenha.Resenha.model.player;


import br.com.resenha.Resenha.model.address.Address;
import br.com.resenha.Resenha.model.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Table(name = "players")
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

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Embedded
    private Address address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlayerStatus status;


    public Player(DataRegisterPlayer data, User user) {
        this.name = data.name();
        this.age = data.age();
        this.email = data.email();
        this.user = user;
        this.address = new Address(data.address());
        this.status = PlayerStatus.ACTIVE;
    }

    public void updateInformation(DataUpadatePlayer data) {
        if (data.name() != null) {
            this.name = data.name();
        }
        if (data.age() != null) {
            this.age = data.age();
        }
        if (data.address() != null) {
            this.address.updateInformation(data.address());
        }

    }

    public void deactivate() {
        this.status = PlayerStatus.INACTIVE;
    }

    public void activate() {
        this.status = PlayerStatus.ACTIVE;
    }
}
