package br.com.resenha.Resenha.model.booking;

import br.com.resenha.Resenha.model.location.court.*;
import br.com.resenha.Resenha.model.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor
public class Booking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "court_id", nullable = false)
    private Court court;
    @ManyToOne(optional = false) @JoinColumn(name = "creator_id", nullable = false)
    private User creator;
    @Column(nullable = false) private LocalDateTime startsAt;
    @Column(nullable = false) private LocalDateTime endsAt;
    @Column(nullable = false) private LocalDateTime createdAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CourtType sport;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private BookingVisibility visibility;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private BookingStatus status;
    @Column(nullable = false) private Integer minimumParticipants;

    @Column(length = 64) private String invitationHash;

    public void rotateInvitation(String hash) { this.invitationHash = hash; }

    public static final BigDecimal SIMULATED_PRICE = new BigDecimal("50.00");

    @Column(precision = 10, scale = 2) private BigDecimal paymentAmount;
    @Column(precision = 10, scale = 2) private BigDecimal paymentDue;
    @Enumerated(EnumType.STRING) private SimulatedPaymentMethod paymentMethod;

    public Booking(Court court, User creator, DataCreateBooking data, LocalDateTime now) {
        this.court = court;
        this.creator = creator;
        this.startsAt = data.startsAt();
        this.endsAt = startsAt.plusHours(1);
        this.createdAt = now;
        this.sport = data.sport();
        this.visibility = data.visibility();
        this.paymentMethod = data.paymentMethod();
        this.paymentDue = SIMULATED_PRICE;
        this.paymentAmount = data.paymentAmount();
        this.status = paymentAmount.compareTo(paymentDue) < 0 ? BookingStatus.PENDING_PAYMENT : BookingStatus.OPEN;
        this.minimumParticipants = court.getMinimumParticipants();
    }
}
