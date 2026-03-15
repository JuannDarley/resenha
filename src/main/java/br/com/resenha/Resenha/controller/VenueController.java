package br.com.resenha.Resenha.controller;

import br.com.resenha.Resenha.model.location.Venue.*;
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
@RequestMapping("/venues")
public class VenueController {

    @Autowired
    private VenueRepository venueRepository;

    @PostMapping
    @Transactional
    public ResponseEntity register(@RequestBody @Valid DataRegisterVenue data, UriComponentsBuilder uriBuilder) {
        var venue = new Venue(data);
        venueRepository.save(venue);
        var uri = uriBuilder.path("/venues/{id}").buildAndExpand(venue.getId()).toUri();
        return ResponseEntity.created(uri).body(new DataDetailVenue(venue));
    }

    @GetMapping
    public ResponseEntity<Page<DataListVenue>> list(@PageableDefault(size = 10, sort = {"name"}) Pageable paginacao) {
        var page = venueRepository.findAll(paginacao).map(DataListVenue::new);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity detail(@PathVariable Long id) {
        var venue = venueRepository.getReferenceById(id);
        return ResponseEntity.ok(new DataDetailVenue(venue));
    }

    @PutMapping
    @Transactional
    public ResponseEntity update(@RequestBody @Valid DataUpdateVenue data) {
        var venue = venueRepository.getReferenceById(data.id());
        venue.updateInformation(data);
        return ResponseEntity.ok(new DataDetailVenue(venue));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity delete(@PathVariable Long id) {
        venueRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

}