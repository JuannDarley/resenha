package br.com.resenha.Resenha.controller;

import br.com.resenha.Resenha.model.location.Venue.VenueRepository;
import br.com.resenha.Resenha.model.location.court.*;
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
@RequestMapping("/courts")
@PreAuthorize("hasRole('OWNER')")
public class CourtController {

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private VenueRepository venueRepository;

    @PostMapping
    @Transactional
    public ResponseEntity register(
            @RequestBody @Valid DataRegisterCourt data,
            UriComponentsBuilder uriBuilder,
            Authentication authentication) {
        User owner = (User) authentication.getPrincipal();

        var venue = venueRepository.findById(data.venueId())
                .filter(v -> v.getOwner().getId().equals(owner.getId()))
                .orElseThrow(() -> new RuntimeException("Venue não encontrada ou não pertence ao usuário"));

        var court = new Court(data, venue);
        courtRepository.save(court);
        var uri = uriBuilder.path("/courts/{id}").buildAndExpand(court.getId()).toUri();
        return ResponseEntity.created(uri).body(new DataDetailCourt(court));
    }

    @GetMapping
    public ResponseEntity<Page<DataDetailCourt>> listar(
            @PageableDefault(size = 10, sort = {"name"}) Pageable paginacao,
            Authentication authentication) {

        User owner = (User) authentication.getPrincipal();

        var page = courtRepository.findAllByVenueOwnerId(owner.getId(), paginacao)
                .map(DataDetailCourt::new);

        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity detail(
            @PathVariable Long id,
            Authentication authentication) {

        User owner = (User) authentication.getPrincipal();

        var court = courtRepository.findById(id)
                .filter(c -> c.getVenue().getOwner().getId().equals(owner.getId()))
                .orElseThrow(() -> new RuntimeException("Court não encontrada ou não pertence ao usuário"));

        return ResponseEntity.ok(new DataDetailCourt(court));
    }

    @PutMapping
    @Transactional
    public ResponseEntity atualizar(
            @RequestBody @Valid DataUpdateCourt data,
            Authentication authentication) {

        User owner = (User) authentication.getPrincipal();

        var court = courtRepository.findById(data.id())
                .filter(c -> c.getVenue().getOwner().getId().equals(owner.getId()))
                .orElseThrow(() -> new RuntimeException("Court não encontrada ou não pertence ao usuário"));

        court.updateInformation(data);

        return ResponseEntity.ok(new DataDetailCourt(court));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity excluir(
            @PathVariable Long id,
            Authentication authentication) {

        User owner = (User) authentication.getPrincipal();

        var court = courtRepository.findById(id)
                .filter(c -> c.getVenue().getOwner().getId().equals(owner.getId()))
                .orElseThrow(() -> new RuntimeException("Court não encontrada ou não pertence ao usuário"));

        courtRepository.delete(court);

        return ResponseEntity.noContent().build();
    }

}