package br.com.resenha.Resenha.model.location.Venue;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VenueRepository extends JpaRepository<Venue, Long> {
    Page<Venue> findAllByOwnerId(Long id, Pageable paginacao);
}