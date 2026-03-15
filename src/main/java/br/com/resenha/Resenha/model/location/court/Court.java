package br.com.resenha.Resenha.model.location.court;

import br.com.resenha.Resenha.model.location.Venue.Venue;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "courts")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Court {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private CourtType type;

    @ManyToOne
    @JoinColumn(name = "venue_id")
    private Venue venue;

    public Court(DataRegisterCourt data, Venue venue) {
        this.name = data.name();
        this.type = data.type();
        this.venue = venue;
    }

    public void updateInformation(DataUpdateCourt data) {
        if (data.name() != null) this.name = data.name();
        if (data.type() != null) this.type = data.type();
    }
}
