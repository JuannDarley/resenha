package br.com.resenha.Resenha.model.location.court;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Range;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface CourtRepository extends JpaRepository<Court, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entity from Court entity where entity.id = :id")
    Optional<Court> findByIdForUpdate(@Param("id") Long id);

    Page<Court> findAllByVenueStatusAndMinimumParticipantsIsNotNull(
            br.com.resenha.Resenha.model.location.Venue.VenueStatus status, Pageable pageable);

    Page<Court> findAllByVenueOwnerId(Long ownerId, Pageable pageable);
}