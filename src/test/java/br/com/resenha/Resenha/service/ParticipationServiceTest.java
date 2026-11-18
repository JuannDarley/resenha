package br.com.resenha.Resenha.service;

import br.com.resenha.Resenha.model.booking.*;
import br.com.resenha.Resenha.model.participation.*;
import br.com.resenha.Resenha.model.player.*;
import br.com.resenha.Resenha.model.user.*;
import br.com.resenha.Resenha.model.location.Venue.*;
import br.com.resenha.Resenha.model.location.court.Court;
import br.com.resenha.Resenha.validation.participation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Pure unit tests: real business validators and clock, mocked persistence; no Spring context or database. */
class ParticipationServiceTest {
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final ParticipationRepository participations = mock(ParticipationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PlayerRepository players = mock(PlayerRepository.class);
    private final MatchDiscoveryService discovery = mock(MatchDiscoveryService.class);
    private final InvitationCodes codes = new InvitationCodes();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-18T11:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private final LocalDateTime now = LocalDateTime.now(clock);
    private final Booking match = mock(Booking.class);
    private final User creator = new User(), visitor = new User();
    private final ParticipationService service = new ParticipationService(bookings, participations, users, players, discovery,
            new ParticipationWindowValidator(), new CreatorPermissionValidator(), new DuplicateParticipationValidator(participations),
            new RequestDecisionValidator(), new InvitationValidator(codes), codes, clock);

    @BeforeEach void setup() {
        creator.setId(1L); visitor.setId(2L); visitor.setEmail("private@example.com");
        var court = mock(Court.class); var venue = mock(Venue.class);
        when(match.getId()).thenReturn(10L);
        when(match.getCreator()).thenReturn(creator);
        when(match.getCourt()).thenReturn(court); when(court.getVenue()).thenReturn(venue);
        when(venue.getStatus()).thenReturn(VenueStatus.APPROVED);
        when(match.getStatus()).thenReturn(BookingStatus.OPEN);
        when(match.getStartsAt()).thenReturn(now.plusHours(3));
        when(match.getVisibility()).thenReturn(BookingVisibility.PRIVATE);
        when(bookings.findByIdForUpdate(10L)).thenReturn(Optional.of(match));
        when(bookings.findById(10L)).thenReturn(Optional.of(match));
        when(users.findById(2L)).thenReturn(Optional.of(visitor));
        when(users.findById(1L)).thenReturn(Optional.of(creator));
        when(participations.saveAndFlush(any(Participation.class))).thenAnswer(invocation -> {
            Participation participation = invocation.getArgument(0);
            ReflectionTestUtils.setField(participation, "id", 20L);
            return participation;
        });
    }
    private Participation existing(ParticipationStatus status) {
        var result = new Participation(match, visitor, status, now.minusMinutes(30));
        ReflectionTestUtils.setField(result, "id", 20L);
        when(participations.findByIdAndBookingId(20L, 10L)).thenReturn(Optional.of(result));
        return result;
    }

    @Test void publicEntryAwaitsPaymentAndUsesServerIdentityAndTimestamps() {
        when(match.getVisibility()).thenReturn(BookingVisibility.PUBLIC);
        var result = service.request(10L, 2L, null);
        assertEquals(ParticipationStatus.PENDING_PAYMENT, result.status());
        assertEquals(10L, result.matchId()); assertEquals(20L, result.id());
        assertEquals(now, result.createdAt()); assertEquals(now, result.updatedAt());
        verify(participations).saveAndFlush(argThat(p -> p.getUser() == visitor
                && p.getBooking() == match && p.getStatus() != ParticipationStatus.CONFIRMED));
    }
    @Test void privateInvitationDoesNotBypassCreatorApproval() {
        String code = codes.generate(); when(match.getInvitationHash()).thenReturn(codes.hash(code));
        assertEquals(ParticipationStatus.REQUESTED, service.request(10L, 2L, code).status());
    }
    @Test void privateEntryCanBeRequestedWithoutInvitation() {
        assertEquals(ParticipationStatus.REQUESTED, service.request(10L, 2L, null).status());
    }
    @Test void creatorMustExplicitlyJoinAndStillAwaitPayment() {
        assertEquals(ParticipationStatus.PENDING_PAYMENT, service.request(10L, 1L, null).status());
    }
    @Test void exactCutoffRejectsEntryWithoutSaving() {
        when(match.getStartsAt()).thenReturn(now.plusHours(1));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.request(10L, 2L, null)).getStatusCode().value());
        verify(participations, never()).saveAndFlush(any()); verifyNoInteractions(users);
    }
    @Test void duplicateEntryDoesNotSaveAnotherParticipation() {
        when(participations.existsByBookingIdAndUserId(10L, 2L)).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.request(10L, 2L, null)).getStatusCode().value());
        verify(participations, never()).saveAndFlush(any());
    }
    @Test void invalidInvitationDoesNotCreateAnEntry() {
        when(match.getInvitationHash()).thenReturn(codes.hash("correct-code"));
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.request(10L, 2L, "wrong-code")).getStatusCode().value());
        verify(participations, never()).saveAndFlush(any());
    }
    @Test void approvalMovesToPendingPaymentAndPreservesCreationTime() {
        var participation = existing(ParticipationStatus.REQUESTED);
        var result = service.decide(10L, 20L, 1L, true);
        assertEquals(ParticipationStatus.PENDING_PAYMENT, result.status());
        assertEquals(now.minusMinutes(30), result.createdAt()); assertEquals(now, result.updatedAt());
        assertEquals(ParticipationStatus.PENDING_PAYMENT, participation.getStatus());
    }
    @Test void rejectionDoesNotConfirmParticipation() {
        var participation = existing(ParticipationStatus.REQUESTED);
        assertEquals(ParticipationStatus.REJECTED, service.decide(10L, 20L, 1L, false).status());
        assertEquals(now, participation.getUpdatedAt());
    }
    @Test void otherPlayersCannotDecideAndDoNotChangeExistingState() {
        var participation = existing(ParticipationStatus.REQUESTED);
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.decide(10L, 20L, 2L, true)).getStatusCode().value());
        assertEquals(ParticipationStatus.REQUESTED, participation.getStatus());
        assertEquals(now.minusMinutes(30), participation.getUpdatedAt());
    }
    @Test void repeatedDecisionCannotReverseApproval() {
        var participation = existing(ParticipationStatus.PENDING_PAYMENT);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.decide(10L, 20L, 1L, false)).getStatusCode().value());
        assertEquals(ParticipationStatus.PENDING_PAYMENT, participation.getStatus());
    }
    @Test void privateIdentityCannotBeReadByAnotherPlayer() {
        when(discovery.visible(10L)).thenReturn(match);
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.participants(10L, 2L, Pageable.unpaged())).getStatusCode().value());
        verify(participations, never()).findAllByBookingIdAndStatus(anyLong(), any(), any());
        verifyNoInteractions(players);
    }
    @Test void publicParticipantListQueriesOnlyConfirmedAndUsesSafeFallbackWithoutProfile() {
        when(match.getVisibility()).thenReturn(BookingVisibility.PUBLIC);
        when(discovery.visible(10L)).thenReturn(match);
        var confirmed = existing(ParticipationStatus.CONFIRMED);
        when(participations.findAllByBookingIdAndStatus(10L, ParticipationStatus.CONFIRMED, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(confirmed)));
        var result = service.participants(10L, 2L, Pageable.unpaged());
        assertEquals(1, result.getTotalElements());
        assertEquals(new DataParticipant(20L, "Jogador"), result.getContent().get(0));
        verify(players).findByUserIdAndStatus(2L, PlayerStatus.ACTIVE);
        assertFalse(result.getContent().toString().contains(visitor.getEmail()));
    }
    @Test void creatorSeesRequestNamesButOutsiderCannotReadThem() {
        var participation = existing(ParticipationStatus.REQUESTED);
        when(participations.findAllByBookingId(10L, Pageable.unpaged())).thenReturn(new PageImpl<>(List.of(participation)));
        var profile = mock(Player.class); when(profile.getName()).thenReturn("Atleta");
        when(players.findByUserIdAndStatus(2L, PlayerStatus.ACTIVE)).thenReturn(Optional.of(profile));
        assertEquals("Atleta", service.requests(10L, 1L, Pageable.unpaged()).getContent().get(0).name());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.requests(10L, 2L, Pageable.unpaged())).getStatusCode().value());
        verify(participations, times(1)).findAllByBookingId(10L, Pageable.unpaged());
    }
    @Test void invitationReturnsCodeButStoresOnlyItsHash() {
        String code = service.invitation(10L, 1L).get("code");
        assertNotNull(code); assertEquals(43, code.length());
        verify(match).rotateInvitation(codes.hash(code));
        verify(match, never()).rotateInvitation(code);
    }
    @Test void missingMatchAndUnknownInvitationAreRejectedWithoutDisclosure() {
        when(bookings.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.request(99L, 2L, null)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.resolve("unknown", 2L)).getStatusCode().value());
        verifyNoInteractions(discovery);
        verify(participations, never()).saveAndFlush(any());
    }
}
