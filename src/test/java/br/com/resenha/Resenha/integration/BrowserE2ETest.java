package br.com.resenha.Resenha.integration;

import br.com.resenha.Resenha.model.user.*;
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

    @Test void frontendFlowsUseRealApiAndDatabase() throws Exception {
        User admin = new User();
        admin.setEmail("admin-e2e-" + UUID.randomUUID() + "@example.com");
        admin.setPassword(encoder.encode("password123")); admin.setRole(Role.ADMIN);
        users.save(admin);
        Process process = new ProcessBuilder("python3", "scripts/browser_e2e.py",
                "--api", "http://localhost:" + port, "--admin-email", admin.getEmail(),
                "--front", System.getProperty("resenha.frontend", "../resenha-front"))
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
