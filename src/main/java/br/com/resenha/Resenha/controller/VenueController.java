package br.com.resenha.Resenha.controller;

import br.com.resenha.Resenha.model.location.Venue.*;
import br.com.resenha.Resenha.model.user.User;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/venues")
@PreAuthorize("hasRole('OWNER')")
public class VenueController {

    @Autowired
    private VenueRepository venueRepository;

    @PostMapping
    @Transactional
    public ResponseEntity register(
            @RequestBody @Valid DataRegisterVenue data,
            UriComponentsBuilder uriBuilder,
            Authentication authentication) {

        User owner = (User) authentication.getPrincipal();

        System.out.println("OWNER ID: " + owner.getId());

        System.out.println("OWNER EMAIL: " + owner.getEmail());

        var venue = new Venue(data, owner);

        venueRepository.save(venue);

        var uri = uriBuilder.path("/venues/{id}")
                .buildAndExpand(venue.getId())
                .toUri();

        return ResponseEntity.created(uri)
                .body(new DataDetailVenue(venue));
    }

    @GetMapping
    public ResponseEntity<Page<DataListVenue>> list(
            @PageableDefault(size = 10, sort = {"name"}) Pageable paginacao,
            Authentication authentication) {

        User owner = (User) authentication.getPrincipal();

        var page = venueRepository.findAllByOwnerId(owner.getId(), paginacao)
                .map(DataListVenue::new);

        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity detail(
            @PathVariable Long id,
            Authentication authentication) {

        User owner = (User) authentication.getPrincipal();

        var venue = venueRepository.findById(id)
                .filter(v -> v.getOwner().getId().equals(owner.getId()))
                .orElseThrow(() -> new RuntimeException("Venue não encontrada ou não pertence ao usuário"));

        return ResponseEntity.ok(new DataDetailVenue(venue));
    }

    @PutMapping
    @Transactional
    public ResponseEntity update(
            @RequestBody @Valid DataUpdateVenue data,
            Authentication authentication) {

        User owner = (User) authentication.getPrincipal();

        var venue = venueRepository.findById(data.id())
                .filter(v -> v.getOwner().getId().equals(owner.getId()))
                .orElseThrow(() -> new RuntimeException("Venue não encontrada ou não pertence ao usuário"));

        venue.updateInformation(data);

        return ResponseEntity.ok(new DataDetailVenue(venue));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity delete(
            @PathVariable Long id,
            Authentication authentication) {

        User owner = (User) authentication.getPrincipal();

        var venue = venueRepository.findById(id)
                .filter(v -> v.getOwner().getId().equals(owner.getId()))
                .orElseThrow(() -> new RuntimeException("Venue não encontrada ou não pertence ao usuário"));

        venueRepository.delete(venue);

        return ResponseEntity.noContent().build();
    }

}