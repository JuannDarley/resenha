package br.com.resenha.Resenha.model.location.availability;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "court_slot_blocks")
@Getter
@NoArgsConstructor
public class CourtSlotBlock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long courtId;
    private LocalDateTime startsAt;
}
