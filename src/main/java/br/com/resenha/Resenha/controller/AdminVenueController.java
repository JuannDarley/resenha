package br.com.resenha.Resenha.controller;

import br.com.resenha.Resenha.model.location.Venue.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/venues")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVenueController {
    private final VenueRepository venues;

    public AdminVenueController(VenueRepository venues) {
        this.venues = venues;
    }

    @GetMapping
    public ResponseEntity<Page<DataListVenue>> list(
            @RequestParam(defaultValue = "PENDING") VenueStatus status,
            @PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return ResponseEntity.ok(venues.findAllByStatus(status, pageable).map(DataListVenue::new));
    }

    @PatchMapping("/{id}/approve")
    @Transactional
    public ResponseEntity<DataDetailVenue> approve(@PathVariable Long id) {
        Venue venue = findVenue(id);
        venue.approve();
        return ResponseEntity.ok(new DataDetailVenue(venue));
    }

    @PatchMapping("/{id}/reject")
    @Transactional
    public ResponseEntity<DataDetailVenue> reject(@PathVariable Long id) {
        Venue venue = findVenue(id);
        venue.reject();
        return ResponseEntity.ok(new DataDetailVenue(venue));
    }

    private Venue findVenue(Long id) {
        return venues.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Estabelecimento não encontrado"));
    }
}
