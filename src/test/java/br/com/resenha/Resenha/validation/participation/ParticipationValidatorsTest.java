package br.com.resenha.Resenha.validation.participation;

import br.com.resenha.Resenha.model.booking.*;
import br.com.resenha.Resenha.model.participation.*;
import br.com.resenha.Resenha.model.location.Venue.*;
import br.com.resenha.Resenha.model.location.court.Court;
import br.com.resenha.Resenha.model.user.User;
import br.com.resenha.Resenha.service.InvitationCodes;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParticipationValidatorsTest {
    private Booking openMatch() {
        var match = mock(Booking.class); var court = mock(Court.class); var venue = mock(Venue.class);
        when(match.getCourt()).thenReturn(court); when(court.getVenue()).thenReturn(venue);
        when(venue.getStatus()).thenReturn(VenueStatus.APPROVED); when(match.getStatus()).thenReturn(BookingStatus.OPEN);
        when(match.getStartsAt()).thenReturn(LocalDateTime.parse("2026-11-18T10:00:00"));
        return match;
    }
    @Test void windowAcceptsOneSecondBeforeCutoffAndRejectsExactBoundaryAndLater() {
        var match = openMatch(); var validator = new ParticipationWindowValidator();
        assertDoesNotThrow(() -> validator.validate(match, LocalDateTime.parse("2026-11-18T08:59:59")));
        for (String now : new String[]{"2026-11-18T09:00:00", "2026-11-18T09:00:01", "2026-11-18T10:00:00"}) {
            assertEquals(409, assertThrows(ResponseStatusException.class,
                    () -> validator.validate(match, LocalDateTime.parse(now))).getStatusCode().value());
        }
    }
    @Test void windowRejectsCancelledAndRevokedVenue() {
        var match = openMatch(); var validator = new ParticipationWindowValidator();
        var now = LocalDateTime.parse("2026-11-17T08:00:00");
        when(match.getStatus()).thenReturn(BookingStatus.CANCELLED);
        assertThrows(ResponseStatusException.class, () -> validator.validate(match, now));
        when(match.getStatus()).thenReturn(BookingStatus.OPEN);
        when(match.getCourt().getVenue().getStatus()).thenReturn(VenueStatus.REJECTED);
        assertThrows(ResponseStatusException.class, () -> validator.validate(match, now));
    }
    @Test void creatorPermissionDoesNotAuthorizeOtherPlayers() {
        var match = mock(Booking.class); var user = new User(); user.setId(42L);
        when(match.getCreator()).thenReturn(user); var validator = new CreatorPermissionValidator();
        assertDoesNotThrow(() -> validator.validate(match, 42L));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> validator.validate(match, 43L)).getStatusCode().value());
    }
    @Test void duplicateRuleRejectsAnyExistingRecordRegardlessOfItsStatus() {
        var repository = mock(ParticipationRepository.class); var validator = new DuplicateParticipationValidator(repository);
        when(repository.existsByBookingIdAndUserId(1L, 2L)).thenReturn(false, true);
        assertDoesNotThrow(() -> validator.validate(1L, 2L));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> validator.validate(1L, 2L)).getStatusCode().value());
    }
    @Test void onlyRequestedEntriesCanBeDecided() {
        var participation = mock(Participation.class); var validator = new RequestDecisionValidator();
        when(participation.getStatus()).thenReturn(ParticipationStatus.REQUESTED);
        assertDoesNotThrow(() -> validator.validate(participation));
        for (var status : new ParticipationStatus[]{ParticipationStatus.PENDING_PAYMENT, ParticipationStatus.REJECTED, ParticipationStatus.CONFIRMED}) {
            when(participation.getStatus()).thenReturn(status);
            assertThrows(ResponseStatusException.class, () -> validator.validate(participation));
        }
    }
    @Test void invitationsAreRandomHashedOptionalAndCannotBeReusedForAnotherMatch() {
        var codes = new InvitationCodes(); String code = codes.generate(), second = codes.generate();
        assertNotEquals(code, second); assertEquals(43, code.length());
        String hash = codes.hash(code); assertEquals(64, hash.length()); assertNotEquals(code, hash);
        assertTrue(codes.matches(code, hash)); assertFalse(codes.matches(second, hash));
        var match = mock(Booking.class); when(match.getVisibility()).thenReturn(BookingVisibility.PRIVATE);
        when(match.getInvitationHash()).thenReturn(hash); var validator = new InvitationValidator(codes);
        assertDoesNotThrow(() -> validator.validate(match, null));
        assertDoesNotThrow(() -> validator.validate(match, code));
        assertThrows(ResponseStatusException.class, () -> validator.validate(match, second));
        assertThrows(ResponseStatusException.class, () -> validator.validate(match, ""));
        when(match.getVisibility()).thenReturn(BookingVisibility.PUBLIC);
        assertThrows(ResponseStatusException.class, () -> validator.validate(match, code));
    }
    @Test void searchRejectsInvalidVenueSubMinuteTimesAndExcessiveCityInput() {
        var validator = new MatchSearchValidator();
        assertDoesNotThrow(() -> validator.validate(null, LocalTime.of(10, 30), "Cidade"));
        assertThrows(ResponseStatusException.class, () -> validator.validate(0L, null, null));
        assertThrows(ResponseStatusException.class, () -> validator.validate(null, LocalTime.of(10, 0, 1), null));
        assertThrows(ResponseStatusException.class, () -> validator.validate(null, null, "X".repeat(101)));
    }
}
