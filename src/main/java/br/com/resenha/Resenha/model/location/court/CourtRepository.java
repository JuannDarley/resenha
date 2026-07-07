package br.com.resenha.Resenha.model.location.court;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Range;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourtRepository extends JpaRepository<Court, Long> {
    Page<Court> findAllByVenueOwnerId(Long ownerId, Pageable pageable);
}