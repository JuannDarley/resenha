package br.com.resenha.Resenha.model.location.Venue;


import br.com.resenha.Resenha.model.address.Address;
import br.com.resenha.Resenha.model.location.court.Court;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "venues")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;
    private String openingTime;
    private String closingTime;
    private Integer courtCount;

    @Embedded
    private Address address;

    @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL)
    private List<Court> courts = new ArrayList<>();

    public Venue(DataRegisterVenue data) {
        this.name = data.name();
        this.description = data.description();
        this.openingTime = data.openingTime();
        this.closingTime = data.closingTime();
        this.courtCount = data.courtCount();
        this.address = new Address(data.address());
    }

    public void updateInformation(DataUpdateVenue data) {
        if (data.name() != null) this.name = data.name();
        if (data.description() != null) this.description = data.description();
        if (data.openingTime() != null) this.openingTime = data.openingTime();
        if (data.closingTime() != null) this.closingTime = data.closingTime();
        if (data.courtCount() != null) this.courtCount = data.courtCount();
        if (data.address() != null) this.address.updateInformation(data.address());
    }
}