package br.com.resenha.Resenha.service;

import br.com.resenha.Resenha.model.booking.*;
import br.com.resenha.Resenha.model.participation.*;
import br.com.resenha.Resenha.model.player.*;
import br.com.resenha.Resenha.model.user.UserRepository;
import br.com.resenha.Resenha.validation.participation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;

@Service
public class ParticipationService {
    private final BookingRepository bookings;
    private final ParticipationRepository participations;
    private final UserRepository users;
    private final PlayerRepository players;
    private final MatchDiscoveryService discovery;
    private final ParticipationWindowValidator window;
    private final CreatorPermissionValidator creator;
    private final DuplicateParticipationValidator duplicates;
    private final RequestDecisionValidator decisions;
    private final InvitationValidator invitation;
    private final InvitationCodes codes;
    private final Clock clock;

    public ParticipationService(BookingRepository bookings, ParticipationRepository participations,
            UserRepository users, PlayerRepository players, MatchDiscoveryService discovery,
            ParticipationWindowValidator window, CreatorPermissionValidator creator,
            DuplicateParticipationValidator duplicates, RequestDecisionValidator decisions,
            InvitationValidator invitation, InvitationCodes codes, Clock clock) {
        this.bookings = bookings; this.participations = participations; this.users = users; this.players = players;
        this.discovery = discovery; this.window = window; this.creator = creator; this.duplicates = duplicates;
        this.decisions = decisions; this.invitation = invitation; this.codes = codes; this.clock = clock;
    }

    @Transactional
    public DataParticipation request(Long id, Long userId, String code) {
        var match = locked(id);
        var now = LocalDateTime.now(clock);
        window.validate(match, now);
        invitation.validate(match, code);
        duplicates.validate(id, userId);
        var user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
        // Creator has no automatic paid participation: explicitly joining still awaits payment.
        var status = match.getVisibility() == BookingVisibility.PRIVATE && !match.getCreator().getId().equals(userId)
                ? ParticipationStatus.REQUESTED : ParticipationStatus.PENDING_PAYMENT;
        return new DataParticipation(participations.saveAndFlush(new Participation(match, user, status, now)));
    }

    @Transactional
    public DataParticipation decide(Long id, Long requestId, Long caller, boolean approved) {
        var match = locked(id);
        creator.validate(match, caller);
        var now = LocalDateTime.now(clock);
        window.validate(match, now);
        var participation = participations.findByIdAndBookingId(requestId, id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação não encontrada"));
        decisions.validate(participation);
        participation.decide(approved, now);
        return new DataParticipation(participation);
    }

    @Transactional(readOnly = true)
    public Page<DataManagedRequest> requests(Long id, Long caller, Pageable pageable) {
        var match = bookings.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partida não encontrada"));
        creator.validate(match, caller);
        return participations.findAllByBookingId(id, pageable)
                .map(p -> new DataManagedRequest(p.getId(), displayName(p), p.getStatus()));
    }

    @Transactional(readOnly = true)
    public Page<DataParticipant> participants(Long id, Long caller, Pageable pageable) {
        var match = discovery.visible(id);
        if (match.getVisibility() == BookingVisibility.PRIVATE) creator.validate(match, caller);
        return participations.findAllByBookingIdAndStatus(id, ParticipationStatus.CONFIRMED, pageable)
                .map(p -> new DataParticipant(p.getId(), displayName(p)));
    }

    @Transactional(readOnly = true)
    public DataParticipation mine(Long id, Long userId) {
        return participations.findByBookingIdAndUserId(id, userId).map(DataParticipation::new).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Você não possui solicitação nesta partida"));
    }

    @Transactional(readOnly = true)
    public Page<DataParticipation> mine(Long userId, Pageable pageable) {
        return participations.findAllByUserId(userId, pageable).map(DataParticipation::new);
    }

    @Transactional
    public java.util.Map<String, String> invitation(Long id, Long caller) {
        var match = locked(id);
        creator.validate(match, caller);
        window.validate(match, LocalDateTime.now(clock));
        if (match.getVisibility() != BookingVisibility.PRIVATE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Convites por código são apenas para partidas privadas");
        }
        String code = codes.generate(); match.rotateInvitation(codes.hash(code));
        return java.util.Map.of("code", code);
    }

    @Transactional(readOnly = true)
    public DataMatch resolve(String code, Long caller) {
        var match = bookings.findByInvitationHash(codes.hash(code)).filter(b -> b.getVisibility() == BookingVisibility.PRIVATE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Convite não encontrado ou revogado"));
        window.validate(match, LocalDateTime.now(clock));
        return discovery.describe(match, caller, LocalDateTime.now(clock));
    }

    private Booking locked(Long id) {
        return bookings.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partida não encontrada"));
    }
    private String displayName(Participation participation) {
        return players.findByUserIdAndStatus(participation.getUser().getId(), PlayerStatus.ACTIVE)
                .map(Player::getName).orElse("Jogador");
    }
}
