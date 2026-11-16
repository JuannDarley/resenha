package br.com.resenha.Resenha.controller;

import br.com.resenha.Resenha.infra.security.TokenConfig;
import br.com.resenha.Resenha.model.player.PlayerRepository;
import br.com.resenha.Resenha.model.user.DataRegisterRequest;
import br.com.resenha.Resenha.model.user.Role;
import br.com.resenha.Resenha.model.user.User;
import br.com.resenha.Resenha.model.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthenticationControllerTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuthenticationController controller = new AuthenticationController(
            users, mock(PlayerRepository.class), encoder,
            mock(AuthenticationManager.class), mock(TokenConfig.class));

    @Test
    void rejectsPublicAdminRegistrationWithoutSavingUser() {
        var response = controller.register(new DataRegisterRequest(
                "admin@example.com", "password123", Role.ADMIN));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verifyNoInteractions(users, encoder);
    }

    @Test
    void allowsPublicPlayerAndOwnerRegistration() {
        when(encoder.encode("password123")).thenReturn("encoded-password");

        for (Role role : new Role[]{Role.PLAYER, Role.OWNER}) {
            var response = controller.register(new DataRegisterRequest(
                    role.name().toLowerCase() + "@example.com", "password123", role));

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertEquals(role, response.getBody().role());
        }
        verify(users, times(2)).save(any(User.class));
    }
}
