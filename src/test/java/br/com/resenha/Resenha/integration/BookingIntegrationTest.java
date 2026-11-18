package br.com.resenha.Resenha.integration;

import br.com.resenha.Resenha.model.user.*;
import br.com.resenha.Resenha.model.location.Venue.*;
import br.com.resenha.Resenha.model.location.court.*;
import br.com.resenha.Resenha.model.address.DataAddress;
import br.com.resenha.Resenha.infra.security.TokenConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
class BookingIntegrationTest extends PostgresTestSupport {
    static class TestClock extends Clock {
        private volatile Instant instant = Instant.parse("2026-11-17T11:00:00Z");
        public ZoneId getZone() { return ZoneId.of("America/Sao_Paulo"); }
        public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        public Instant instant() { return instant; }
        void set(String value) { instant = Instant.parse(value); }
    }
    @TestConfiguration
    static class Config {
        @Bean @Primary TestClock bookingTestClock() { return new TestClock(); }
    }
    @LocalServerPort int port;
    @Autowired TestClock clock;
    @Autowired UserRepository users;
    @Autowired VenueRepository venues;
    @Autowired CourtRepository courts;
    @Autowired TokenConfig tokens;
    @Autowired JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private User player, owner;
    private Court court;

    @BeforeEach void setup() {
        clock.set("2026-11-17T11:00:00Z"); // 08:00 in São Paulo, independent of machine/test date.
        player = user(Role.PLAYER); owner = user(Role.OWNER);
        Venue venue = new Venue(new DataRegisterVenue("Arena", "Local", "08:00", "23:00", 1,
                new DataAddress("Rua", "Centro", "12345678", "Cidade", "SP", null, "1")), owner);
        venue.approve(); venues.save(venue);
        court = courts.save(new Court(new DataRegisterCourt("Quadra", CourtType.SOCIETY, venue.getId(), 10), venue));
    }
    private User user(Role role) {
        User user = new User(); user.setEmail(UUID.randomUUID() + "@example.com");
        user.setRole(role); user.setPassword("unused-password-hash"); return users.save(user);
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
    private String body(Long courtId, String start) {
        return "{\"courtId\":" + courtId + ",\"startsAt\":\"" + start + "\",\"sport\":\"SOCIETY\",\"visibility\":\"PUBLIC\",\"paymentMethod\":\"PIX\",\"paymentAmount\":50}";
    }
    private JsonNode create(User user, Long courtId, String start) throws Exception {
        return expect(201, request("POST", "/bookings", user, body(courtId, start)));
    }
    private long count(User user) {
        return jdbc.queryForObject("select count(*) from bookings where creator_id = ?", Long.class, user.getId());
    }
    private void hours(String opening, String closing) throws Exception {
        expect(200, request("PUT", "/venues", owner, "{\"id\":" + court.getVenue().getId()
                + ",\"openingTime\":\"" + opening + "\",\"closingTime\":\"" + closing + "\"}"));
    }

    @Test void validatesSimulatedPaymentAndPersistsPartialAmount() throws Exception {
        String payload = body(court.getId(), "2026-11-18T10:00:00");
        for (String invalid : List.of("null", "0", "-1", "1.001")) {
            expect(400, request("POST", "/bookings", player, payload.replace(":50}", ":" + invalid + "}")));
        }
        expect(400, request("POST", "/bookings", player, payload.replace("PIX", "INVALID")));
        assertEquals(0, count(player));
        var pending = expect(201, request("POST", "/bookings", player, payload.replace(":50}", ":49.99}")));
        assertEquals("PENDING_PAYMENT", pending.get("status").asText());
        var detail = expect(200, request("GET", "/bookings/" + pending.get("id").asLong(), player, null));
        assertEquals("49.99", detail.get("paymentAmount").asText());
        assertEquals("PIX", detail.get("paymentMethod").asText());
        expect(409, request("POST", "/bookings", player, payload));
    }

    @Test void createsOpenMatchWithSnapshotsAndIsolatesHistory() throws Exception {
        String payload = body(court.getId(), "2026-11-17T16:00:00").replace("PUBLIC", "PRIVATE")
                .replace("}", ",\"status\":\"CANCELLED\",\"creatorId\":" + owner.getId() + ",\"minimumParticipants\":1}");
        var response = request("POST", "/bookings", player, payload);
        var booking = expect(201, response); long id = booking.get("id").asLong();
        assertTrue(response.headers().firstValue("Location").orElseThrow().endsWith("/bookings/" + id));
        assertEquals("OPEN", booking.get("status").asText());
        assertEquals("PRIVATE", booking.get("visibility").asText());
        assertEquals("2026-11-17T17:00:00", booking.get("endsAt").asText());
        assertEquals("2026-11-17T08:00:00", booking.get("createdAt").asText());
        assertEquals(10, booking.get("minimumParticipants").asInt());
        assertEquals(1, count(player)); assertEquals(0, count(owner));
        var slots = expect(200, request("GET", "/courts/" + court.getId() + "/availability?date=2026-11-17", owner, null));
        for (var slot : slots) {
            if (slot.get("startsAt").asText().equals("2026-11-17T16:00:00")) {
                assertEquals("RESERVED", slot.get("status").asText());
                assertFalse(slot.get("available").asBoolean());
            }
        }
        expect(200, request("GET", "/bookings/" + id, player, null));
        assertEquals(1, expect(200, request("GET", "/bookings", player, null)).get("totalElements").asInt());
        User other = user(Role.PLAYER);
        expect(404, request("GET", "/bookings/" + id, other, null));
        assertEquals(0, expect(200, request("GET", "/bookings", other, null)).get("totalElements").asInt());
        expect(200, request("PUT", "/courts", owner, "{\"id\":" + court.getId() + ",\"minimumParticipants\":12}"));
        assertEquals(10, expect(200, request("GET", "/bookings/" + id, player, null)).get("minimumParticipants").asInt());
    }

    @Test void rn01AcceptsExactlyEightHoursButRejectsLessPastAndLateCreation() throws Exception {
        for (String start : List.of("2026-11-17T15:00:00", "2026-11-17T08:00:00", "2026-11-16T16:00:00")) {
            expect(400, request("POST", "/bookings", player, body(court.getId(), start)));
        }
        assertEquals(0, count(player));
        create(player, court.getId(), "2026-11-17T16:00:00");
        clock.set("2026-11-17T21:00:01Z"); // 18:00:01 local
        expect(400, request("POST", "/bookings", player, body(court.getId(), "2026-11-17T22:00:00")));
        assertEquals(1, count(player));
    }

    @Test void tomorrowCanBeLessThanEightHoursAwayAndUsePreviousOpeningDate() throws Exception {
        hours("22:00", "02:00");
        clock.set("2026-11-18T02:30:00Z"); // 23:30 on the 17th, not the 18th in São Paulo.
        create(player, court.getId(), "2026-11-18T00:00:00");
        var slots = expect(200, request("GET", "/availability/courts/" + court.getId() + "?date=2026-11-17", player, null));
        assertEquals(2, slots.size());
        assertFalse(slots.get(0).get("available").asBoolean());
        assertTrue(slots.get(1).get("available").asBoolean());
    }

    @Test void dailyLimitCountsCancelledAndAllScheduledDatesButResetsNextDay() throws Exception {
        long id = create(player, court.getId(), "2026-11-18T10:00:00").get("id").asLong();
        // Cancellation policy is not exposed yet; seed the state solely to exercise RF08.
        jdbc.update("update bookings set status = 'CANCELLED' where id = ?", id);
        create(player, court.getId(), "2026-11-19T10:00:00");
        create(player, court.getId(), "2026-11-20T10:00:00");
        expect(409, request("POST", "/bookings", player, body(court.getId(), "2026-11-21T10:00:00")));
        assertEquals(3, count(player));
        create(user(Role.PLAYER), court.getId(), "2026-11-18T10:00:00"); // Cancelled slot is free.
        clock.set("2026-11-18T03:00:00Z"); // Midnight local: a new creation day.
        create(player, court.getId(), "2026-11-21T10:00:00");
        assertEquals(4, count(player));
    }

    @Test void requiresEligibleCourtValidGridVisibilityAndPlayerRole() throws Exception {
        String payload = body(court.getId(), "2026-11-18T10:00:00");
        expect(403, request("POST", "/bookings", owner, payload));
        expect(403, request("POST", "/bookings", user(Role.ADMIN), payload));
        expect(403, request("POST", "/bookings", null, payload));
        expect(400, request("POST", "/bookings", player, "{}"));
        expect(400, request("POST", "/bookings", player, payload.replace("PUBLIC", "INVALID")));
        expect(400, request("POST", "/bookings", player, payload.replace("SOCIETY", "VOLEI")));
        expect(404, request("POST", "/bookings", player, body(999999L, "2026-11-18T10:00:00")));
        for (String start : List.of("2026-11-18T07:00:00", "2026-11-18T23:00:00", "2026-11-18T10:30:00", "2026-11-18T10:00:01")) {
            expect(400, request("POST", "/bookings", player, body(court.getId(), start)));
        }
        for (String status : List.of("PENDING", "REJECTED")) {
            jdbc.update("update venues set status = ? where id = ?", status, court.getVenue().getId());
            expect(409, request("POST", "/bookings", player, payload));
        }
        jdbc.update("update venues set status = 'APPROVED' where id = ?", court.getVenue().getId());
        jdbc.update("update courts set minimum_participants = null where id = ?", court.getId());
        expect(409, request("POST", "/bookings", player, payload));
        assertEquals(0, count(player));
    }

    @Test void manualBlocksAndReservationsCannotOverrideOneAnotherAndHistoryPreventsDeletion() throws Exception {
        String path = "/courts/" + court.getId() + "/availability";
        String block = "{\"startsAt\":\"2026-11-18T10:00:00\",\"available\":false}";
        expect(204, request("PUT", path, owner, block));
        expect(409, request("POST", "/bookings", player, body(court.getId(), "2026-11-18T10:00:00")));
        expect(204, request("PUT", path, owner, block.replace("false", "true")));
        create(player, court.getId(), "2026-11-18T10:00:00");
        expect(409, request("PUT", path, owner, block));
        expect(409, request("PUT", path, owner, block.replace("false", "true")));
        expect(409, request("DELETE", "/courts/" + court.getId(), owner, null));
        expect(409, request("DELETE", "/venues/" + court.getVenue().getId(), owner, null));
        assertEquals(1, count(player));
    }

    private List<Integer> parallel(List<Callable<Integer>> tasks) throws Exception {
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            var futures = tasks.stream().map(task -> executor.submit(() -> { start.await(); return task.call(); })).toList();
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(25, TimeUnit.SECONDS));
            return results;
        } finally { executor.shutdownNow(); }
    }

    @Test void concurrentUsersCannotDoubleBookAndAdjacentSlotsRemainFree() throws Exception {
        User other = user(Role.PLAYER);
        var statuses = parallel(List.of(
                () -> request("POST", "/bookings", player, body(court.getId(), "2026-11-18T10:00:00")).statusCode(),
                () -> request("POST", "/bookings", other, body(court.getId(), "2026-11-18T10:00:00")).statusCode()));
        assertEquals(1, Collections.frequency(statuses, 201), statuses.toString());
        assertEquals(1, Collections.frequency(statuses, 409), statuses.toString());
        create(other, court.getId(), "2026-11-18T11:00:00");
    }

    @Test void concurrentRequestsAcrossCourtsCannotExceedDailyQuota() throws Exception {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Long id = courts.save(new Court(new DataRegisterCourt("Quadra " + i, CourtType.SOCIETY, court.getVenue().getId(), 10), court.getVenue())).getId();
            tasks.add(() -> request("POST", "/bookings", player, body(id, "2026-11-18T10:00:00")).statusCode());
        }
        var statuses = parallel(tasks);
        assertEquals(3, Collections.frequency(statuses, 201), statuses.toString());
        assertEquals(1, Collections.frequency(statuses, 409), statuses.toString());
        assertEquals(3, count(player));
    }

    @Test void bookingAndOwnerBlockRaceMustHaveExactlyOneWinner() throws Exception {
        String path = "/courts/" + court.getId() + "/availability";
        var statuses = parallel(List.of(
                () -> request("POST", "/bookings", player, body(court.getId(), "2026-11-18T10:00:00")).statusCode(),
                () -> request("PUT", path, owner, "{\"startsAt\":\"2026-11-18T10:00:00\",\"available\":false}").statusCode()));
        assertEquals(1, Collections.frequency(statuses, 409), statuses.toString());
        assertTrue(statuses.contains(201) || statuses.contains(204), statuses.toString());
        long blockCount = jdbc.queryForObject("select count(*) from court_slot_blocks where court_id = ?", Long.class, court.getId());
        assertEquals(1, count(player) + blockCount);
    }

    @Test void databaseAndServiceRejectOverlapsAfterOperatingHourChanges() throws Exception {
        long id = create(player, court.getId(), "2026-11-18T10:00:00").get("id").asLong();
        hours("08:30", "23:30");
        expect(409, request("POST", "/bookings", user(Role.PLAYER), body(court.getId(), "2026-11-18T10:30:00")));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into bookings(court_id,creator_id,starts_at,ends_at,created_at,sport,visibility,status,minimum_participants) "
                + "select court_id,creator_id,starts_at + interval '30 minutes',ends_at + interval '30 minutes',created_at,sport,visibility,status,minimum_participants from bookings where id = ?", id));
    }
    @Test void discoveryListsOnlyApprovedConfiguredCourtsWithoutOwnerIdentity() throws Exception {
        var data = expect(200, request("GET", "/availability/courts?size=100", player, null));
        boolean found = false;
        for (var item : data.get("content")) {
            assertFalse(item.has("owner")); assertFalse(item.has("creator"));
            if (item.get("id").asLong() == court.getId()) found = true;
        }
        assertTrue(found);
        jdbc.update("update venues set status = 'PENDING' where id = ?", court.getVenue().getId());
        data = expect(200, request("GET", "/availability/courts?size=100", player, null));
        for (var item : data.get("content")) assertNotEquals(court.getId().longValue(), item.get("id").asLong());
        jdbc.update("update venues set status = 'APPROVED' where id = ?", court.getVenue().getId());
        jdbc.update("update courts set minimum_participants = null where id = ?", court.getId());
        data = expect(200, request("GET", "/availability/courts?size=100", player, null));
        for (var item : data.get("content")) assertNotEquals(court.getId().longValue(), item.get("id").asLong());
    }

}
