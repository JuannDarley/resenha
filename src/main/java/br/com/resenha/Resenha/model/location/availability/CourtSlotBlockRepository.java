package br.com.resenha.Resenha.model.location.availability;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface CourtSlotBlockRepository extends JpaRepository<CourtSlotBlock, Long> {
    List<CourtSlotBlock> findAllByCourtIdAndStartsAtGreaterThanAndStartsAtLessThan(
            Long courtId, LocalDateTime from, LocalDateTime to);

    @Modifying
    @Query(value = "INSERT INTO court_slot_blocks (court_id, starts_at) VALUES (:court, :start) ON CONFLICT (court_id, starts_at) DO NOTHING", nativeQuery = true)
    void block(@Param("court") Long court, @Param("start") LocalDateTime start);

    @Modifying
    @Query(value = "DELETE FROM court_slot_blocks WHERE court_id = :court AND starts_at < :end AND starts_at > :before", nativeQuery = true)
    void unblock(@Param("court") Long court, @Param("before") LocalDateTime before, @Param("end") LocalDateTime end);
}
