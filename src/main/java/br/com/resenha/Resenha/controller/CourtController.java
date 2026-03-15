package br.com.resenha.Resenha.controller;

import br.com.resenha.Resenha.model.location.Venue.VenueRepository;
import br.com.resenha.Resenha.model.location.court.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/courts")
public class CourtController {

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private VenueRepository venueRepository;

    @PostMapping
    @Transactional
    public ResponseEntity register(@RequestBody @Valid DataRegisterCourt data, UriComponentsBuilder uriBuilder) {
        var venue = venueRepository.getReferenceById(data.venueId());
        var court = new Court(data, venue);
        courtRepository.save(court);
        var uri = uriBuilder.path("/courts/{id}").buildAndExpand(court.getId()).toUri();
        return ResponseEntity.created(uri).body(new DataDetailCourt(court));
    }

    @GetMapping
    public ResponseEntity<Page<DataDetailCourt>> listar(@PageableDefault(size = 10, sort = {"name"}) Pageable paginacao) {
        var page = courtRepository.findAll(paginacao).map(DataDetailCourt::new);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity detail(@PathVariable Long id) {
        var court = courtRepository.getReferenceById(id);
        return ResponseEntity.ok(new DataDetailCourt(court));
    }

    @PutMapping
    @Transactional
    public ResponseEntity atualizar(@RequestBody @Valid DataUpdateCourt data) {
        var court = courtRepository.getReferenceById(data.id());
        court.updateInformation(data);
        return ResponseEntity.ok(new DataDetailCourt(court));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity excluir(@PathVariable Long id) {
        courtRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

}