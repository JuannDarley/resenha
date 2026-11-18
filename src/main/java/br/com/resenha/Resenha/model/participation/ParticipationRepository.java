package br.com.resenha.Resenha.model.participation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.Optional;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {
    boolean existsByBookingIdAndUserId(Long bookingId, Long userId);
    Optional<Participation> findByBookingIdAndUserId(Long bookingId, Long userId);
    Optional<Participation> findByIdAndBookingId(Long id, Long bookingId);
    Page<Participation> findAllByBookingId(Long bookingId, Pageable pageable);
    Page<Participation> findAllByBookingIdAndStatus(Long bookingId, ParticipationStatus status, Pageable pageable);
    Page<Participation> findAllByUserId(Long userId, Pageable pageable);
    long countByBookingIdAndStatus(Long bookingId, ParticipationStatus status);
}
