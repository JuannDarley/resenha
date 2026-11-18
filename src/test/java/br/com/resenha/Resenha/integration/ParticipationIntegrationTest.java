package br.com.resenha.Resenha.integration;

import br.com.resenha.Resenha.model.booking.*;
import br.com.resenha.Resenha.model.participation.*;
import br.com.resenha.Resenha.model.user.*;
import br.com.resenha.Resenha.model.location.Venue.*;
import br.com.resenha.Resenha.model.location.court.*;
import br.com.resenha.Resenha.model.address.DataAddress;
import br.com.resenha.Resenha.model.player.*;
import br.com.resenha.Resenha.infra.security.TokenConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(BookingIntegrationTest.Config.class)
class ParticipationIntegrationTest extends PostgresTestSupport {
    @LocalServerPort int port;
    @Autowired BookingIntegrationTest.TestClock clock;
    @Autowired UserRepository users;
    @Autowired PlayerRepository players;
    @Autowired VenueRepository venues;
    @Autowired CourtRepository courts;
    @Autowired BookingRepository bookings;
    @Autowired ParticipationRepository participations;
    @Autowired TokenConfig tokens;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private User creator, visitor;
    private Court court;
    private final DataAddress address = new DataAddress("Rua", "Centro", "12345678", "Cidade", "SP", null, "1");

    @BeforeEach void setup() {
        clock.set("2026-11-17T11:00:00Z");
        creator = user(Role.PLAYER); visitor = user(Role.PLAYER);
        players.save(new Player(new DataRegisterPlayer("Criador sigiloso", 25L, creator.getEmail(), address), creator));
        players.save(new Player(new DataRegisterPlayer("Visitante sigiloso", 25L, visitor.getEmail(), address), visitor));
        User owner = user(Role.OWNER);
        Venue venue = new Venue(new DataRegisterVenue("Arena", "Local", "08:00", "23:00", 1, address), owner);
        venue.approve(); venues.save(venue);
        court = courts.save(new Court(new DataRegisterCourt("Quadra", CourtType.VOLEI, venue.getId(), 12), venue));
    }
    private User user(Role role) {
        User user = new User(); user.setEmail(UUID.randomUUID() + "@example.com"); user.setPassword("unused-hash");
        user.setRole(role); return users.save(user);
    }
    private Booking match(BookingVisibility visibility) { return match(visibility, "2026-11-18T10:00:00"); }
    private Booking match(BookingVisibility visibility, String start) {
        return bookings.save(new Booking(court, creator,
                new DataCreateBooking(court.getId(), LocalDateTime.parse(start), CourtType.VOLEI, visibility, SimulatedPaymentMethod.PIX, Booking.SIMULATED_PRICE), LocalDateTime.now(clock)));
    }
    private HttpResponse<String> request(String method, String path, User user, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json");
        if (user != null) builder.header("Authorization", "Bearer " + tokens.generateToken(user));
        return http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode expect(int status, HttpResponse<String> response) {
        assertEquals(status, response.statusCode(), response.body());
        return response.body().isBlank() ? json.nullNode() : json.readTree(response.body());
    }
    private JsonNode join(Booking match, User user) throws Exception {
        return expect(201, request("POST", "/matches/" + match.getId() + "/participations", user, "{}"));
    }
    private String code(Booking match) throws Exception {
        return expect(200, request("POST", "/matches/" + match.getId() + "/invitation", creator, null)).get("code").asText();
    }
    private void noIdentity(JsonNode data) {
        String value = data.toString();
        for (String secret : List.of("Criador sigiloso", "Visitante sigiloso", creator.getEmail(), visitor.getEmail(),
                "invitationHash", "invitationCode", "password", "userId", "creatorId")) assertFalse(value.contains(secret), value);
    }

    @Test void discoveryFiltersDateTimeVenueCitySportAndExcludesPastCancelledAndUnapproved() throws Exception {
        Booking first = match(BookingVisibility.PUBLIC);
        match(BookingVisibility.PRIVATE, "2026-11-18T11:00:00");
        match(BookingVisibility.PUBLIC, "2026-11-19T10:00:00");
        match(BookingVisibility.PUBLIC, "2026-11-16T10:00:00");
        Booking cancelled = match(BookingVisibility.PUBLIC, "2026-11-18T12:00:00");
        jdbc.update("update bookings set status = 'CANCELLED' where id = ?", cancelled.getId());
        String filter = "/matches?venueId=" + court.getVenue().getId();
        assertEquals(3, expect(200, request("GET", filter, visitor, null)).get("totalElements").asInt());
        var data = expect(200, request("GET", filter + "&date=2026-11-18&time=10:00&sport=VOLEI&city=cidade", visitor, null));
        assertEquals(1, data.get("totalElements").asInt());
        assertEquals(first.getId().longValue(), data.get("content").get(0).get("id").asLong());
        noIdentity(data);
        assertEquals(0, expect(200, request("GET", filter + "&sport=BASQUETE", visitor, null)).get("totalElements").asInt());
        assertEquals(2, expect(200, request("GET", filter + "&time=10:00", visitor, null)).get("totalElements").asInt());
        jdbc.update("update venues set status = 'PENDING' where id = ?", court.getVenue().getId());
        assertEquals(0, expect(200, request("GET", filter, visitor, null)).get("totalElements").asInt());
        expect(404, request("GET", "/matches/" + first.getId(), visitor, null));
    }

    @Test void publicEntryAwaitsPaymentAndCannotForgeConfirmationOrDecision() throws Exception {
        Booking match = match(BookingVisibility.PUBLIC);
        var response = expect(201, request("POST", "/matches/" + match.getId() + "/participations", visitor,
                "{\"status\":\"CONFIRMED\",\"userId\":" + creator.getId() + "}"));
        assertEquals("PENDING_PAYMENT", response.get("status").asText());
        long id = response.get("id").asLong();
        var detail = expect(200, request("GET", "/matches/" + match.getId(), visitor, null));
        assertEquals(0, detail.get("participantCount").asInt());
        assertEquals("OPEN", detail.get("status").asText());
        expect(409, request("PATCH", "/matches/" + match.getId() + "/requests/" + id, creator, "{\"approved\":true}"));
        expect(409, request("POST", "/matches/" + match.getId() + "/participations", visitor, "{}"));
        assertEquals(1, participations.countByBookingIdAndStatus(match.getId(), ParticipationStatus.PENDING_PAYMENT));
        assertEquals(0, participations.countByBookingIdAndStatus(match.getId(), ParticipationStatus.CONFIRMED));
    }

    @Test void privateRequestsRequireCreatorApprovalAndStayUnpaidAfterApproval() throws Exception {
        Booking match = match(BookingVisibility.PRIVATE);
        var participation = join(match, visitor);
        assertEquals("REQUESTED", participation.get("status").asText());
        String path = "/matches/" + match.getId() + "/requests";
        expect(403, request("GET", path, visitor, null));
        expect(403, request("PATCH", path + "/" + participation.get("id").asLong(), visitor, "{\"approved\":true}"));
        var managed = expect(200, request("GET", path, creator, null));
        assertEquals("Visitante sigiloso", managed.get("content").get(0).get("name").asText());
        var approved = expect(200, request("PATCH", path + "/" + participation.get("id").asLong(), creator,
                "{\"approved\":true,\"status\":\"CONFIRMED\"}"));
        assertEquals("PENDING_PAYMENT", approved.get("status").asText());
        assertEquals("PENDING_PAYMENT", expect(200, request("GET", "/matches/" + match.getId() + "/participation/me", visitor, null)).get("status").asText());
        expect(409, request("PATCH", path + "/" + participation.get("id").asLong(), creator, "{\"approved\":false}"));
        assertEquals(0, expect(200, request("GET", "/matches/" + match.getId(), visitor, null)).get("participantCount").asInt());
    }

    @Test void rejectsPrivateRequestAndPreventsResubmissionOrCrossMatchDecisions() throws Exception {
        Booking match = match(BookingVisibility.PRIVATE);
        long id = join(match, visitor).get("id").asLong();
        Booking other = match(BookingVisibility.PRIVATE, "2026-11-18T11:00:00");
        expect(404, request("PATCH", "/matches/" + other.getId() + "/requests/" + id, creator, "{\"approved\":true}"));
        var result = expect(200, request("PATCH", "/matches/" + match.getId() + "/requests/" + id, creator, "{\"approved\":false}"));
        assertEquals("REJECTED", result.get("status").asText());
        expect(409, request("POST", "/matches/" + match.getId() + "/participations", visitor, "{}"));
        assertEquals(1, participations.countByBookingIdAndStatus(match.getId(), ParticipationStatus.REJECTED));
    }

    @Test void invitationsAreHashedRotatableAndNeverBypassApproval() throws Exception {
        Booking match = match(BookingVisibility.PRIVATE);
        String first = code(match);
        String hash = jdbc.queryForObject("select invitation_hash from bookings where id = ?", String.class, match.getId());
        assertNotEquals(first, hash); assertEquals(64, hash.length());
        var resolved = expect(200, request("POST", "/match-invitations/resolve", visitor, "{\"code\":\"" + first + "\"}"));
        noIdentity(resolved);
        String second = code(match); assertNotEquals(first, second);
        expect(404, request("POST", "/match-invitations/resolve", visitor, "{\"code\":\"" + first + "\"}"));
        expect(400, request("POST", "/matches/" + match.getId() + "/participations", visitor, "{\"invitationCode\":\"" + first + "\"}"));
        var response = expect(201, request("POST", "/matches/" + match.getId() + "/participations", visitor,
                "{\"invitationCode\":\"" + second + "\"}"));
        assertEquals("REQUESTED", response.get("status").asText());
        expect(403, request("POST", "/matches/" + match.getId() + "/invitation", visitor, null));
        Booking publicMatch = match(BookingVisibility.PUBLIC, "2026-11-18T11:00:00");
        expect(400, request("POST", "/matches/" + publicMatch.getId() + "/invitation", creator, null));
    }

    @Test void exactTMinusOneHourBlocksRequestsApprovalsAndInvitationRotation() throws Exception {
        Booking match = match(BookingVisibility.PRIVATE);
        clock.set("2026-11-18T11:59:59Z"); // 08:59:59 local, one second before T−1h.
        long id = join(match, visitor).get("id").asLong();
        String code = code(match);
        clock.set("2026-11-18T12:00:00Z"); // Exactly 09:00 local for 10:00 game.
        expect(409, request("POST", "/matches/" + match.getId() + "/participations", user(Role.PLAYER), "{}"));
        expect(409, request("PATCH", "/matches/" + match.getId() + "/requests/" + id, creator, "{\"approved\":true}"));
        expect(409, request("POST", "/matches/" + match.getId() + "/invitation", creator, null));
        expect(409, request("POST", "/match-invitations/resolve", visitor, "{\"code\":\"" + code + "\"}"));
        assertFalse(expect(200, request("GET", "/matches/" + match.getId(), visitor, null)).get("joinable").asBoolean());
        assertEquals(1, participations.countByBookingIdAndStatus(match.getId(), ParticipationStatus.REQUESTED));
    }

    @Test void privateIdentityIsNeverRevealedToOtherPlayersEvenWithConfirmedParticipation() throws Exception {
        Booking match = match(BookingVisibility.PRIVATE);
        participations.save(new Participation(match, visitor, ParticipationStatus.CONFIRMED, LocalDateTime.now(clock)));
        var outsider = user(Role.PLAYER);
        var detail = expect(200, request("GET", "/matches/" + match.getId(), outsider, null));
        noIdentity(detail); assertEquals(1, detail.get("participantCount").asInt());
        noIdentity(expect(200, request("GET", "/matches?venueId=" + court.getVenue().getId(), outsider, null)));
        expect(403, request("GET", "/matches/" + match.getId() + "/participants", outsider, null));
        expect(403, request("GET", "/matches/" + match.getId() + "/participants", visitor, null));
        var data = expect(200, request("GET", "/matches/" + match.getId() + "/participants", creator, null));
        assertEquals("Visitante sigiloso", data.get("content").get(0).get("name").asText());
    }

    @Test void publicParticipantsContainOnlyConfirmedPlayersAndNoSensitiveFields() throws Exception {
        Booking match = match(BookingVisibility.PUBLIC);
        join(match, creator); // Creator must explicitly join and still await payment.
        participations.save(new Participation(match, visitor, ParticipationStatus.CONFIRMED, LocalDateTime.now(clock)));
        var response = expect(200, request("GET", "/matches/" + match.getId() + "/participants", creator, null));
        assertEquals(1, response.get("totalElements").asInt());
        assertEquals("Visitante sigiloso", response.get("content").get(0).get("name").asText());
        assertFalse(response.toString().contains(visitor.getEmail()));
        assertFalse(response.toString().contains("userId"));
        assertEquals(1, expect(200, request("GET", "/matches/" + match.getId(), creator, null)).get("participantCount").asInt());
    }

    @Test void ownRequestsHistoryIsIsolatedAndNonPlayersHaveNoAccess() throws Exception {
        Booking match = match(BookingVisibility.PRIVATE); join(match, visitor);
        assertEquals(1, expect(200, request("GET", "/participations/me", visitor, null)).get("totalElements").asInt());
        assertEquals(0, expect(200, request("GET", "/participations/me", creator, null)).get("totalElements").asInt());
        expect(404, request("GET", "/matches/" + match.getId() + "/participation/me", creator, null));
        for (User other : List.of(user(Role.OWNER), user(Role.ADMIN))) {
            expect(403, request("GET", "/matches", other, null));
            expect(403, request("POST", "/matches/" + match.getId() + "/participations", other, "{}"));
            expect(403, request("POST", "/matches/" + match.getId() + "/invitation", other, null));
            expect(403, request("GET", "/participations/me", other, null));
        }
        expect(403, request("GET", "/matches", null, null));
    }

    @Test void cancelledUnapprovedAndMissingGamesCannotAcceptRequests() throws Exception {
        Booking match = match(BookingVisibility.PUBLIC);
        jdbc.update("update bookings set status = 'CANCELLED' where id = ?", match.getId());
        expect(409, request("POST", "/matches/" + match.getId() + "/participations", visitor, "{}"));
        jdbc.update("update bookings set status = 'OPEN' where id = ?", match.getId());
        jdbc.update("update venues set status = 'REJECTED' where id = ?", court.getVenue().getId());
        expect(409, request("POST", "/matches/" + match.getId() + "/participations", visitor, "{}"));
        expect(404, request("POST", "/matches/999999/participations", visitor, "{}"));
        assertEquals(0, participations.countByBookingIdAndStatus(match.getId(), ParticipationStatus.PENDING_PAYMENT));
    }

    @Test void validatesFiltersDecisionBodiesAndInvitationPayloads() throws Exception {
        Booking match = match(BookingVisibility.PRIVATE);
        for (String filter : List.of("date=bad", "time=09:00:01", "venueId=-1", "sport=INVALID")) {
            expect(400, request("GET", "/matches?" + filter, visitor, null));
        }
        long id = join(match, visitor).get("id").asLong();
        expect(400, request("PATCH", "/matches/" + match.getId() + "/requests/" + id, creator, "{}"));
        expect(400, request("POST", "/match-invitations/resolve", visitor, "{\"code\":\"\"}"));
        expect(400, request("POST", "/matches/" + match.getId() + "/participations", creator, "{\"invitationCode\":\"\"}"));
    }

    @Test void simultaneousDuplicateRequestsCreateExactlyOneRecordAndUniqueConstraintProtectsTheDatabase() throws Exception {
        Booking match = match(BookingVisibility.PRIVATE);
        ExecutorService pool = Executors.newFixedThreadPool(3); CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> responses = new ArrayList<>();
            for (int i = 0; i < 3; i++) responses.add(pool.submit(() -> { start.await();
                return request("POST", "/matches/" + match.getId() + "/participations", visitor, "{}").statusCode(); }));
            start.countDown(); List<Integer> statuses = new ArrayList<>();
            for (var response : responses) statuses.add(response.get(25, TimeUnit.SECONDS));
            assertEquals(1, Collections.frequency(statuses, 201), statuses.toString());
            assertEquals(2, Collections.frequency(statuses, 409), statuses.toString());
            assertEquals(1, participations.countByBookingIdAndStatus(match.getId(), ParticipationStatus.REQUESTED));
        } finally { pool.shutdownNow(); }
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into participations(booking_id,user_id,status,created_at,updated_at) values (?,?,'CONFIRMED',current_timestamp,current_timestamp)", match.getId(), visitor.getId()));
    }
    @Test void competingApprovalAndRejectionCommitOnlyOneDecision() throws Exception {
        Booking match = match(BookingVisibility.PRIVATE);
        long id = join(match, visitor).get("id").asLong();
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> approve = pool.submit(() -> { start.await(); return request("PATCH", "/matches/" + match.getId()
                    + "/requests/" + id, creator, "{\"approved\":true}").statusCode(); });
            Future<Integer> reject = pool.submit(() -> { start.await(); return request("PATCH", "/matches/" + match.getId()
                    + "/requests/" + id, creator, "{\"approved\":false}").statusCode(); });
            start.countDown(); var statuses = List.of(approve.get(25, TimeUnit.SECONDS), reject.get(25, TimeUnit.SECONDS));
            assertEquals(1, Collections.frequency(statuses, 200), statuses.toString());
            assertEquals(1, Collections.frequency(statuses, 409), statuses.toString());
            var status = participations.findById(id).orElseThrow().getStatus();
            assertTrue(status == ParticipationStatus.PENDING_PAYMENT || status == ParticipationStatus.REJECTED);
            assertEquals(0, participations.countByBookingIdAndStatus(match.getId(), ParticipationStatus.CONFIRMED));
        } finally { pool.shutdownNow(); }
    }

}
