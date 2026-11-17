package br.com.resenha.Resenha.model.booking;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    long countByCreatorIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long creatorId, LocalDateTime from, LocalDateTime to);
    Page<Booking> findAllByCreatorId(Long creatorId, Pageable pageable);
    Optional<Booking> findByIdAndCreatorId(Long id, Long creatorId);

    @Query("select b from Booking b where b.court.id = :court and b.status <> br.com.resenha.Resenha.model.booking.BookingStatus.CANCELLED and b.startsAt < :end and b.endsAt > :start")
    List<Booking> findOverlapping(@Param("court") Long court, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
