package br.com.resenha.Resenha.model.participation;

import java.time.LocalDateTime;

public record DataParticipation(Long id, Long matchId, ParticipationStatus status,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
    public DataParticipation(Participation participation) {
        this(participation.getId(), participation.getBooking().getId(), participation.getStatus(),
                participation.getCreatedAt(), participation.getUpdatedAt());
    }
}
