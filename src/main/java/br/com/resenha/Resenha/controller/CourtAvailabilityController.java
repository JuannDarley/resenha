package br.com.resenha.Resenha.controller;

import br.com.resenha.Resenha.model.location.availability.*;
import br.com.resenha.Resenha.model.user.User;
import br.com.resenha.Resenha.service.CourtAvailabilityService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import java.util.List;

@RestController
public class CourtAvailabilityController {
    private final CourtAvailabilityService availability;
    public CourtAvailabilityController(CourtAvailabilityService availability) { this.availability = availability; }

    @GetMapping("/courts/{id}/availability")
    @PreAuthorize("hasRole('OWNER')")
    public List<DataSlot> ownerSlots(@PathVariable Long id, @AuthenticationPrincipal User owner,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return availability.ownerSlots(id, owner.getId(), date);
    }

    @PutMapping("/courts/{id}/availability")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> updateSlot(@PathVariable Long id, @AuthenticationPrincipal User owner,
            @RequestBody @Valid DataSlotUpdate data) {
        availability.updateSlot(id, owner.getId(), data);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/availability/courts")
    @PreAuthorize("hasRole('PLAYER')")
    public Page<DataAvailableCourt> availableCourts(@PageableDefault(size = 10, sort = "name") Pageable pageable) {
        return availability.availableCourts(pageable);
    }

    @GetMapping("/availability/courts/{id}")
    @PreAuthorize("hasRole('PLAYER')")
    public List<DataSlot> playerSlots(@PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return availability.playerSlots(id, date);
    }
}
