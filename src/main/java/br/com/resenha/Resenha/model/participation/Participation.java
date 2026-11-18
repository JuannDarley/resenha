package br.com.resenha.Resenha.model.participation;

import br.com.resenha.Resenha.model.booking.Booking;
import br.com.resenha.Resenha.model.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "participations")
@Getter
@NoArgsConstructor
public class Participation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "booking_id", nullable = false) private Booking booking;
    @ManyToOne(optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ParticipationStatus status;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    public Participation(Booking booking, User user, ParticipationStatus status, LocalDateTime now) {
        this.booking = booking; this.user = user; this.status = status;
        this.createdAt = now; this.updatedAt = now;
    }
    public void decide(boolean approved, LocalDateTime now) {
        status = approved ? ParticipationStatus.PENDING_PAYMENT : ParticipationStatus.REJECTED;
        updatedAt = now;
    }
}
