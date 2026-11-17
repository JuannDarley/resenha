package br.com.resenha.Resenha.integration;

import br.com.resenha.Resenha.model.user.*;
import br.com.resenha.Resenha.model.location.Venue.*;
import br.com.resenha.Resenha.model.location.court.*;
import br.com.resenha.Resenha.model.address.DataAddress;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@Tag("browser")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrowserE2ETest extends PostgresTestSupport {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired VenueRepository venues;
    @Autowired CourtRepository courts;

    @Test void frontendFlowsUseRealApiAndDatabase() throws Exception {
        User admin = new User();
        admin.setEmail("admin-e2e-" + UUID.randomUUID() + "@example.com");
        admin.setPassword(encoder.encode("password123")); admin.setRole(Role.ADMIN);
        users.save(admin);
        User owner = new User(); owner.setEmail("owner-e2e-" + UUID.randomUUID() + "@example.com");
        owner.setPassword(encoder.encode("password123")); owner.setRole(Role.OWNER); users.save(owner);
        Venue venue = new Venue(new DataRegisterVenue("Arena disponível E2E", "Local aprovado", "08:00", "22:00", 1,
                new DataAddress("Rua", "Centro", "12345678", "Cidade", "SP", null, "1")), owner);
        venue.approve(); venues.save(venue);
        Court court = courts.save(new Court(new DataRegisterCourt("000 Quadra E2E", CourtType.VOLEI, venue.getId(), 12), venue));
        Process process = new ProcessBuilder("python3", "scripts/browser_e2e.py",
                "--api", "http://localhost:" + port, "--admin-email", admin.getEmail(),
                "--booking-court", court.getId().toString(), "--booking-owner-email", owner.getEmail(), "--front", System.getProperty("resenha.frontend", "../resenha-front"))
                .inheritIO().start();
        try {
            assertTrue(process.waitFor(5, TimeUnit.MINUTES), "Browser E2E timed out");
            assertEquals(0, process.exitValue(), "Browser E2E failed; see output above");
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }
}
