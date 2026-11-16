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
                "{\"name\":\"Quadra\",\"type\":\"SOCIETY\",\"venueId\":" + venue + "}")).get("id").asLong();
        expect(200, request("PUT", "/courts", owner, "{\"id\":" + court + ",\"name\":\"Quadra nova\",\"type\":\"FUTSAL\"}"));
        assertEquals("Quadra nova", expect(200, request("GET", "/courts/" + court, owner, null)).get("name").asText());
        expect(204, request("DELETE", "/courts/" + court, owner, null));
        assertEquals(0, jdbc.queryForObject("select count(*) from courts where id = ?", Integer.class, court));
        expect(201, request("POST", "/courts", owner, "{\"name\":\"Outra\",\"type\":\"CAMPO\",\"venueId\":" + venue + "}"));
        expect(204, request("DELETE", "/venues/" + venue, owner, null));
        assertEquals(0, jdbc.queryForObject("select count(*) from courts where venue_id = ?", Integer.class, venue));
    }

    @Test void ownersCannotReadModifyOrDeleteEachOthersResources() throws Exception {
        String owner = account("OWNER"), other = account("OWNER"); long id = venue(owner);
        long court = expect(201, request("POST", "/courts", owner,
                "{\"name\":\"Privada\",\"type\":\"SOCIETY\",\"venueId\":" + id + "}")).get("id").asLong();
        assertEquals(0, expect(200, request("GET", "/venues", other, null)).get("totalElements").asInt());
        assertEquals(0, expect(200, request("GET", "/courts", other, null)).get("totalElements").asInt());
        expect(404, request("GET", "/venues/" + id, other, null));
        expect(404, request("PUT", "/venues", other, "{\"id\":" + id + ",\"name\":\"Ataque\"}"));
        expect(404, request("DELETE", "/venues/" + id, other, null));
        expect(404, request("POST", "/courts", other, "{\"name\":\"Ataque\",\"type\":\"CAMPO\",\"venueId\":" + id + "}"));
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
        assertEquals(10, jdbc.queryForObject("select count(*) from flyway_schema_history where success = true", Integer.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("update venues set status = 'INVALID' where id = ?", id));
    }
}
