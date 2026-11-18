package br.com.resenha.Resenha.controller;

import br.com.resenha.Resenha.model.participation.*;
import br.com.resenha.Resenha.model.location.court.CourtType;
import br.com.resenha.Resenha.model.user.User;
import br.com.resenha.Resenha.service.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.*;

@RestController
@PreAuthorize("hasRole('PLAYER')")
public class MatchController {
    private final MatchDiscoveryService discovery;
    private final ParticipationService participations;
    public MatchController(MatchDiscoveryService discovery, ParticipationService participations) {
        this.discovery = discovery; this.participations = participations;
    }
    @GetMapping("/matches")
    public Page<DataMatch> list(@AuthenticationPrincipal User user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
            @RequestParam(required = false) Long venueId, @RequestParam(required = false) String city,
            @RequestParam(required = false) CourtType sport,
            @PageableDefault(size = 10, sort = "startsAt") Pageable pageable) {
        return discovery.list(user.getId(), date, time, venueId, city, sport, pageable);
    }
    @GetMapping("/matches/{id}")
    public DataMatch detail(@PathVariable Long id, @AuthenticationPrincipal User user) { return discovery.detail(id, user.getId()); }
    @PostMapping("/matches/{id}/participations")
    public ResponseEntity<DataParticipation> join(@PathVariable Long id, @AuthenticationPrincipal User user,
            @Valid @RequestBody DataJoinRequest data) {
        var result = participations.request(id, user.getId(), data.invitationCode());
        return ResponseEntity.created(java.net.URI.create("/matches/" + id + "/participation/me")).body(result);
    }
    @GetMapping("/matches/{id}/participation/me")
    public DataParticipation mine(@PathVariable Long id, @AuthenticationPrincipal User user) { return participations.mine(id, user.getId()); }
    @GetMapping("/participations/me")
    public Page<DataParticipation> mine(@AuthenticationPrincipal User user,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return participations.mine(user.getId(), pageable);
    }
    @GetMapping("/matches/{id}/requests")
    public Page<DataManagedRequest> requests(@PathVariable Long id, @AuthenticationPrincipal User user,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return participations.requests(id, user.getId(), pageable);
    }
    @PatchMapping("/matches/{id}/requests/{requestId}")
    public DataParticipation decide(@PathVariable Long id, @PathVariable Long requestId,
            @AuthenticationPrincipal User user, @Valid @RequestBody DataDecision data) {
        return participations.decide(id, requestId, user.getId(), data.approved());
    }
    @GetMapping("/matches/{id}/participants")
    public Page<DataParticipant> participants(@PathVariable Long id, @AuthenticationPrincipal User user,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return participations.participants(id, user.getId(), pageable);
    }
    @PostMapping("/matches/{id}/invitation")
    public java.util.Map<String, String> invitation(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return participations.invitation(id, user.getId());
    }
    @PostMapping("/match-invitations/resolve")
    public DataMatch resolve(@Valid @RequestBody DataResolveInvitation data, @AuthenticationPrincipal User user) {
        return participations.resolve(data.code(), user.getId());
    }
}
