package br.com.resenha.Resenha.infra.security;

import br.com.resenha.Resenha.model.user.User;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenConfigTest {
    private final TokenConfig tokens = new TokenConfig();

    @Test void generatedTokenContainsUserIdentityAndExpiration() {
        User user = new User(); user.setId(42L); user.setEmail("player@example.com");
        String token = tokens.generateToken(user);
        var identity = tokens.validateToken(token).orElseThrow();
        assertEquals(42L, identity.userId());
        assertEquals(user.getEmail(), identity.email());
        var decoded = JWT.decode(token);
        assertTrue(decoded.getExpiresAtAsInstant().isAfter(Instant.now().plusSeconds(86300)));
        assertTrue(decoded.getIssuedAtAsInstant().isBefore(Instant.now().plusSeconds(1)));
    }

    @Test void expiredTokenIsRejected() {
        String token = JWT.create().withClaim("userId", 42L).withSubject("player@example.com")
                .withExpiresAt(Instant.now().minusSeconds(60)).sign(Algorithm.HMAC256("secret"));
        assertTrue(tokens.validateToken(token).isEmpty());
    }

    @Test void wrongSignatureAndMalformedTokenAreRejected() {
        String token = JWT.create().withClaim("userId", 42L).withSubject("player@example.com")
                .sign(Algorithm.HMAC256("another-test-secret"));
        assertTrue(tokens.validateToken(token).isEmpty());
        assertTrue(tokens.validateToken("invalid-token").isEmpty());
        assertTrue(tokens.validateToken("").isEmpty());
    }
}
