package br.com.resenha.Resenha.integration;

import br.com.resenha.Resenha.model.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTest extends PostgresTestSupport {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private static final String ADDRESS = """
        {"street":"Rua A","neighborhood":"Centro","zipCode":"12345678",
         "city":"Cidade","state":"SP","number":"1"}
        """;

    private HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode expect(int status, HttpResponse<String> response) {
        assertEquals(status, response.statusCode(), response.body());
        return response.body().isBlank() ? json.nullNode() : json.readTree(response.body());
    }
    private String account(String role) throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        if (role.equals("ADMIN")) {
            User user = new User(); user.setEmail(email); user.setRole(Role.ADMIN);
            user.setPassword(encoder.encode("password123")); users.save(user);
        } else {
            expect(201, request("POST", "/auth/register", null,
                    "{\"email\":\"" + email + "\",\"password\":\"password123\",\"role\":\"" + role + "\"}"));
            assertTrue(encoder.matches("password123", users.findUserByEmail(email).orElseThrow().getPassword()));
        }
        return expect(200, request("POST", "/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"password123\"}")).get("token").asText();
    }
    private long venue(String token) throws Exception {
        var response = request("POST", "/venues", token, """
            {"name":"Arena","description":"Local esportivo","openingTime":"08:00",
             "closingTime":"22:00","courtCount":1,"address":%s}
            """.formatted(ADDRESS));
        var data = expect(201, response);
        assertEquals("PENDING", data.get("status").asText());
        assertTrue(response.headers().firstValue("Location").orElseThrow().endsWith("/venues/" + data.get("id").asLong()));
        return data.get("id").asLong();
    }
    private long player(String token) throws Exception {
        return expect(201, request("POST", "/players", token, """
            {"name":"Jogador","age":25,"email":"player@example.com","address":%s}
            """.formatted(ADDRESS))).get("id").asLong();
    }

    @Test void publicRegistrationRejectsAdminDuplicateAndInvalidPayload() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String body = "{\"email\":\"" + email + "\",\"password\":\"password123\",\"role\":\"ADMIN\"}";
        expect(403, request("POST", "/auth/register", null, body));
        assertTrue(users.findUserByEmail(email).isEmpty());
        body = body.replace("ADMIN", "PLAYER");
        expect(201, request("POST", "/auth/register", null, body));
        expect(400, request("POST", "/auth/register", null, body));
        expect(400, request("POST", "/auth/register", null, "{\"email\":\"bad\",\"password\":\"\",\"role\":\"OWNER\"}"));
        expect(400, request("POST", "/auth/register", null, "{}"));
    }

    @Test void protectedEndpointsRejectMissingInvalidAndDeletedUserTokens() throws Exception {
        for (String path : new String[]{"/players/me", "/venues", "/courts", "/admin/venues", "/admin/players"}) {
            expect(403, request("GET", path, null, null));
            expect(403, request("GET", path, "invalid-token", null));
        }
        String token = account("OWNER");
        var decoded = com.auth0.jwt.JWT.decode(token);
        users.deleteById(decoded.getClaim("userId").asLong());
        expect(403, request("GET", "/venues", token, null));
    }

    @Test void wrongPasswordDoesNotIssueToken() throws Exception {
        expect(403, request("POST", "/auth/login", null,
                "{\"email\":\"missing@example.com\",\"password\":\"wrong\"}"));
    }

    @Test void rolesCannotAccessOtherRolesEndpoints() throws Exception {
        String player = account("PLAYER"), owner = account("OWNER"), admin = account("ADMIN");
        for (String token : new String[]{player, owner}) {
            expect(403, request("GET", "/admin/players", token, null));
            expect(403, request("GET", "/admin/venues", token, null));
            expect(403, request("PATCH", "/admin/venues/1/approve", token, null));
            expect(403, request("PATCH", "/admin/venues/1/reject", token, null));
        }
        expect(403, request("GET", "/venues", player, null));
        expect(403, request("GET", "/courts", player, null));
        expect(403, request("GET", "/players/me", owner, null));
        expect(403, request("GET", "/venues", admin, null));
    }

    @Test void venueCrudAndApprovalPersistAcrossRequests() throws Exception {
        String owner = account("OWNER"), admin = account("ADMIN");
        long id = venue(owner);
        assertTrue(expect(200, request("GET", "/admin/venues", admin, null)).get("content").toString().contains("\"id\":" + id));
        expect(200, request("PATCH", "/admin/venues/" + id + "/approve", admin, null));
        assertEquals("APPROVED", expect(200, request("GET", "/venues/" + id, owner, null)).get("status").asText());
        expect(200, request("PUT", "/venues", owner, "{\"id\":" + id + ",\"name\":\"Arena nova\",\"status\":\"PENDING\"}"));
        var detail = expect(200, request("GET", "/venues/" + id, owner, null));
        assertEquals("Arena nova", detail.get("name").asText());
        assertEquals("APPROVED", detail.get("status").asText());
        expect(200, request("PATCH", "/admin/venues/" + id + "/reject", admin, null));
        assertEquals("REJECTED", expect(200, request("GET", "/venues/" + id, owner, null)).get("status").asText());
        expect(404, request("PATCH", "/admin/venues/999999/approve", admin, null));
        expect(204, request("DELETE", "/venues/" + id, owner, null));
        assertEquals(0, jdbc.queryForObject("select count(*) from venues where id = ?", Integer.class, id));
    }

    @Test void pendingOwnerCanManageCourtsAndDeleteVenueWithCourts() throws Exception {
        String owner = account("OWNER"); long venue = venue(owner);
        long court = expect(201, request("POST", "/courts", owner,
                "{\"name\":\"Quadra\",\"type\":\"SOCIETY\",\"minimumParticipants\":10,\"venueId\":" + venue + "}")).get("id").asLong();
        expect(200, request("PUT", "/courts", owner, "{\"id\":" + court + ",\"name\":\"Quadra nova\",\"type\":\"FUTSAL\"}"));
        assertEquals("Quadra nova", expect(200, request("GET", "/courts/" + court, owner, null)).get("name").asText());
        expect(204, request("DELETE", "/courts/" + court, owner, null));
        assertEquals(0, jdbc.queryForObject("select count(*) from courts where id = ?", Integer.class, court));
        expect(201, request("POST", "/courts", owner, "{\"name\":\"Outra\",\"type\":\"CAMPO\",\"minimumParticipants\":10,\"venueId\":" + venue + "}"));
        expect(204, request("DELETE", "/venues/" + venue, owner, null));
        assertEquals(0, jdbc.queryForObject("select count(*) from courts where venue_id = ?", Integer.class, venue));
    }

    @Test void ownersCannotReadModifyOrDeleteEachOthersResources() throws Exception {
        String owner = account("OWNER"), other = account("OWNER"); long id = venue(owner);
        long court = expect(201, request("POST", "/courts", owner,
                "{\"name\":\"Privada\",\"type\":\"SOCIETY\",\"minimumParticipants\":10,\"venueId\":" + id + "}")).get("id").asLong();
        assertEquals(0, expect(200, request("GET", "/venues", other, null)).get("totalElements").asInt());
        assertEquals(0, expect(200, request("GET", "/courts", other, null)).get("totalElements").asInt());
        expect(404, request("GET", "/venues/" + id, other, null));
        expect(404, request("PUT", "/venues", other, "{\"id\":" + id + ",\"name\":\"Ataque\"}"));
        expect(404, request("DELETE", "/venues/" + id, other, null));
        expect(404, request("POST", "/courts", other, "{\"name\":\"Ataque\",\"type\":\"CAMPO\",\"minimumParticipants\":10,\"venueId\":" + id + "}"));
        expect(404, request("GET", "/courts/" + court, other, null));
        expect(404, request("PUT", "/courts", other, "{\"id\":" + court + ",\"name\":\"Ataque\"}"));
        expect(404, request("DELETE", "/courts/" + court, other, null));
        assertEquals("Arena", expect(200, request("GET", "/venues/" + id, owner, null)).get("name").asText());
        assertEquals("Privada", expect(200, request("GET", "/courts/" + court, owner, null)).get("name").asText());
    }

    @Test void playerProfileUpdateIsolationAndDeactivation() throws Exception {
        String first = account("PLAYER"), second = account("PLAYER");
        long id = player(first); player(second);
        expect(409, request("POST", "/players", first, "{\"name\":\"Duplicado\",\"age\":25,\"email\":\"x@example.com\",\"address\":" + ADDRESS + "}"));
        expect(200, request("PUT", "/players/me", first, "{\"name\":\"Nome atualizado\",\"age\":30}"));
        var detail = expect(200, request("GET", "/players/me", first, null));
        assertEquals(id, detail.get("id").asLong());
        assertEquals("Nome atualizado", detail.get("name").asText());
        assertEquals(30, detail.get("age").asInt());
        assertEquals("Jogador", expect(200, request("GET", "/players/me", second, null)).get("name").asText());
        expect(204, request("DELETE", "/players/me", first, null));
        expect(404, request("GET", "/players/me", first, null));
        assertEquals("INACTIVE", jdbc.queryForObject("select status from players where id = ?", String.class, id));
    }

    @Test void adminCanManageActivePlayers() throws Exception {
        long id = player(account("PLAYER")); String admin = account("ADMIN");
        expect(200, request("GET", "/admin/players", admin, null));
        expect(200, request("PUT", "/admin/players/" + id, admin, "{\"name\":\"Editado pelo admin\"}"));
        assertEquals("Editado pelo admin", expect(200, request("GET", "/admin/players/" + id, admin, null)).get("name").asText());
        expect(204, request("DELETE", "/admin/players/" + id, admin, null));
        assertEquals("INACTIVE", jdbc.queryForObject("select status from players where id = ?", String.class, id));
        expect(404, request("GET", "/admin/players/" + id, admin, null));
        expect(404, request("PUT", "/admin/players/" + id, admin, "{\"name\":\"Inativo\"}"));
        expect(409, request("DELETE", "/admin/players/" + id, admin, null));
        expect(404, request("DELETE", "/admin/players/999999", admin, null));
    }

    @Test void invalidVenueAndCourtPayloadsAreRejected() throws Exception {
        String owner = account("OWNER");
        expect(400, request("POST", "/venues", owner, "{}"));
        expect(400, request("POST", "/courts", owner, "{}"));
        expect(400, request("POST", "/courts", owner, "{\"name\":\"A\",\"type\":\"INVALID\",\"venueId\":1}"));
    }

    @Test void migrationsAndDatabaseConstraintsAreActive() throws Exception {
        long id = venue(account("OWNER"));
        assertEquals(14, jdbc.queryForObject("select count(*) from flyway_schema_history where success = true", Integer.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("update venues set status = 'INVALID' where id = ?", id));
    }
    private long configuredCourt(String owner, long venue, String type) throws Exception {
        return expect(201, request("POST", "/courts", owner,
                "{\"name\":\"Quadra\",\"type\":\"" + type + "\",\"minimumParticipants\":12,\"venueId\":" + venue + "}"))
                .get("id").asLong();
    }

    @Test void courtModalitiesMinimumAndAvailabilityArePersisted() throws Exception {
        String owner = account("OWNER"); long venue = venue(owner);
        for (String type : new String[]{"SOCIETY", "CAMPO", "FUTSAL", "VOLEI", "BASQUETE"}) {
            long court = configuredCourt(owner, venue, type);
            var detail = expect(200, request("GET", "/courts/" + court, owner, null));
            assertEquals(type, detail.get("type").asText());
            assertEquals(12, detail.get("minimumParticipants").asInt());
            assertEquals(venue, detail.get("venueId").asLong());
            expect(200, request("PUT", "/courts", owner, "{\"id\":" + court + ",\"minimumParticipants\":6}"));
            assertEquals(6, expect(200, request("GET", "/courts/" + court, owner, null)).get("minimumParticipants").asInt());
        }
        expect(400, request("POST", "/courts", owner, "{\"name\":\"Sem mínimo\",\"type\":\"VOLEI\",\"venueId\":" + venue + "}"));
        long court = configuredCourt(owner, venue, "VOLEI");
        expect(400, request("PUT", "/courts", owner, "{\"id\":" + court + ",\"minimumParticipants\":0}"));
        expect(400, request("PUT", "/courts", owner, "{\"id\":" + court + ",\"minimumParticipants\":-1}"));
    }

    @Test void slotBlockingIsIdempotentPersistentAndCascadesOnDeletion() throws Exception {
        String owner = account("OWNER"); long court = configuredCourt(owner, venue(owner), "BASQUETE");
        String date = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(1).toString();
        String path = "/courts/" + court + "/availability";
        assertEquals(14, expect(200, request("GET", path + "?date=" + date, owner, null)).size());
        String block = "{\"startsAt\":\"" + date + "T08:00:00\",\"available\":false}";
        // Simultaneous retries must not create duplicates or fail on the unique constraint.
        var attempts = java.util.stream.IntStream.range(0, 3).mapToObj(i -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try { return request("PUT", path, owner, block); } catch (Exception e) { throw new RuntimeException(e); }
        })).toList();
        for (var attempt : attempts) expect(204, attempt.join());
        assertEquals(1, jdbc.queryForObject("select count(*) from court_slot_blocks where court_id = ?", Integer.class, court));
        var slots = expect(200, request("GET", path + "?date=" + date, owner, null));
        assertFalse(slots.get(0).get("available").asBoolean());
        assertTrue(slots.get(1).get("available").asBoolean());
        expect(204, request("PUT", path, owner, block.replace("false", "true")));
        assertTrue(expect(200, request("GET", path + "?date=" + date, owner, null)).get(0).get("available").asBoolean());
        expect(204, request("PUT", path, owner, block));
        expect(204, request("DELETE", "/courts/" + court, owner, null));
        assertEquals(0, jdbc.queryForObject("select count(*) from court_slot_blocks where court_id = ?", Integer.class, court));
    }

    @Test void slotPermissionsAndApprovalAreEnforcedOverHttp() throws Exception {
        String owner = account("OWNER"), other = account("OWNER"), player = account("PLAYER"), admin = account("ADMIN");
        long venue = venue(owner), court = configuredCourt(owner, venue, "VOLEI");
        String date = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(1).toString();
        String path = "/courts/" + court + "/availability", publicPath = "/availability/courts/" + court + "?date=" + date;
        String block = "{\"startsAt\":\"" + date + "T08:00:00\",\"available\":false}";
        expect(404, request("GET", path + "?date=" + date, other, null));
        expect(404, request("PUT", path, other, block));
        expect(403, request("PUT", path, player, block));
        expect(403, request("GET", path + "?date=" + date, player, null));
        expect(403, request("GET", publicPath, null, null));
        expect(404, request("GET", publicPath, player, null));
        expect(200, request("PATCH", "/admin/venues/" + venue + "/approve", admin, null));
        expect(204, request("PUT", path, owner, block));
        assertFalse(expect(200, request("GET", publicPath, player, null)).get(0).get("available").asBoolean());
        expect(200, request("PATCH", "/admin/venues/" + venue + "/reject", admin, null));
        expect(404, request("GET", publicPath, player, null));
    }

    @Test void invalidTimesDatesSlotsAndUnconfiguredLegacyCourtsAreRejected() throws Exception {
        String owner = account("OWNER"); long venue = venue(owner), court = configuredCourt(owner, venue, "SOCIETY");
        String path = "/courts/" + court + "/availability";
        String date = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(1).toString();
        for (String time : new String[]{"25:00", "08:0", "22:00", "21:30"}) {
            expect(400, request("PUT", "/venues", owner, "{\"id\":" + venue + ",\"openingTime\":\"" + time + "\"}"));
        }
        expect(400, request("GET", path + "?date=bad", owner, null));
        expect(400, request("GET", path, owner, null));
        expect(400, request("PUT", path, owner, "{}"));
        expect(400, request("PUT", path, owner, "{\"startsAt\":\"" + date + "T08:30:00\",\"available\":false}"));
        expect(400, request("PUT", path, owner, "{\"startsAt\":\"" + date + "T22:00:00\",\"available\":false}"));
        jdbc.update("update courts set minimum_participants = null where id = ?", court);
        expect(409, request("GET", path + "?date=" + date, owner, null));
        expect(200, request("PUT", "/courts", owner, "{\"id\":" + court + ",\"minimumParticipants\":10}"));
        assertEquals(14, expect(200, request("GET", path + "?date=" + date, owner, null)).size());
    }

    @Test void overnightSlotsPersistBlocksOnTheFollowingCalendarDay() throws Exception {
        String owner = account("OWNER"); long venue = venue(owner), court = configuredCourt(owner, venue, "VOLEI");
        expect(200, request("PUT", "/venues", owner,
                "{\"id\":" + venue + ",\"openingTime\":\"22:00\",\"closingTime\":\"02:00\"}"));
        var date = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(1);
        String path = "/courts/" + court + "/availability";
        var slots = expect(200, request("GET", path + "?date=" + date, owner, null));
        assertEquals(4, slots.size());
        assertEquals(date.plusDays(1) + "T02:00:00", slots.get(3).get("endsAt").asText());
        String body = "{\"startsAt\":\"" + date.plusDays(1) + "T00:00:00\",\"available\":false}";
        expect(204, request("PUT", path, owner, body));
        slots = expect(200, request("GET", path + "?date=" + date, owner, null));
        assertFalse(slots.get(2).get("available").asBoolean());
        assertTrue(slots.get(1).get("available").asBoolean());
        expect(204, request("PUT", path, owner, body.replace("false", "true")));
        assertTrue(expect(200, request("GET", path + "?date=" + date, owner, null)).get(2).get("available").asBoolean());
    }

    @Test void changingOperatingHoursPreservesOverlappingBlocksAndRejectsObsoleteSlots() throws Exception {
        String owner = account("OWNER"); long venue = venue(owner), court = configuredCourt(owner, venue, "BASQUETE");
        String date = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(1).toString();
        String path = "/courts/" + court + "/availability";
        String block = "{\"startsAt\":\"" + date + "T09:00:00\",\"available\":false}";
        expect(204, request("PUT", path, owner, block));
        expect(200, request("PUT", "/venues", owner,
                "{\"id\":" + venue + ",\"openingTime\":\"08:30\",\"closingTime\":\"11:45\"}"));
        var slots = expect(200, request("GET", path + "?date=" + date, owner, null));
        assertEquals(3, slots.size());
        assertEquals(date + "T11:30:00", slots.get(2).get("endsAt").asText());
        assertFalse(slots.get(0).get("available").asBoolean());
        assertFalse(slots.get(1).get("available").asBoolean());
        assertTrue(slots.get(2).get("available").asBoolean());
        expect(400, request("PUT", path, owner, block));
        expect(204, request("PUT", path, owner,
                "{\"startsAt\":\"" + date + "T08:30:00\",\"available\":true}"));
        slots = expect(200, request("GET", path + "?date=" + date, owner, null));
        assertTrue(slots.get(0).get("available").asBoolean());
        assertTrue(slots.get(1).get("available").asBoolean());
    }

    @Test void blocksAreIsolatedByCourtAndDateAndDeletedWithVenue() throws Exception {
        String owner = account("OWNER"); long venue = venue(owner);
        long first = configuredCourt(owner, venue, "SOCIETY"), second = configuredCourt(owner, venue, "CAMPO");
        var date = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(1);
        String path = "/courts/" + first + "/availability";
        expect(204, request("PUT", path, owner,
                "{\"startsAt\":\"" + date + "T08:00:00\",\"available\":false}"));
        assertTrue(expect(200, request("GET", "/courts/" + second + "/availability?date=" + date, owner, null))
                .get(0).get("available").asBoolean());
        assertTrue(expect(200, request("GET", path + "?date=" + date.plusDays(1), owner, null))
                .get(0).get("available").asBoolean());
        expect(204, request("DELETE", "/venues/" + venue, owner, null));
        assertEquals(0, jdbc.queryForObject("select count(*) from court_slot_blocks where court_id = ?", Integer.class, first));
        expect(404, request("GET", path + "?date=" + date, owner, null));
    }

    @Test void databaseRejectsInvalidMinimumAndDanglingSlotBlocks() throws Exception {
        String owner = account("OWNER"); long court = configuredCourt(owner, venue(owner), "VOLEI");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("update courts set minimum_participants = 0 where id = ?", court));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("insert into court_slot_blocks (court_id, starts_at) values (?, current_timestamp)", 999999L));
        assertEquals(12, jdbc.queryForObject("select minimum_participants from courts where id = ?", Integer.class, court));
    }

}
