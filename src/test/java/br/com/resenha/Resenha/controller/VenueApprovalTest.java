package br.com.resenha.Resenha.controller;

import br.com.resenha.Resenha.model.location.Venue.*;
import br.com.resenha.Resenha.model.user.User;
import br.com.resenha.Resenha.model.address.DataAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(VenueApprovalTest.Config.class)
class VenueApprovalTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean VenueRepository venues() { return mock(VenueRepository.class); }
        @Bean AdminVenueController admin(VenueRepository venues) { return new AdminVenueController(venues); }
        @Bean VenueController owner(VenueRepository venues) {
            VenueController controller = new VenueController();
            ReflectionTestUtils.setField(controller, "venueRepository", venues);
            return controller;
        }
    }

    @Autowired VenueRepository venues;
    @Autowired AdminVenueController admin;
    @Autowired VenueController ownerController;

    @BeforeEach void resetMocks() { reset(venues); }
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    private UsernamePasswordAuthenticationToken login(String role, long id) {
        User user = new User();
        user.setId(id);
        var auth = new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
        return auth;
    }

    private Venue newVenue() {
        User owner = new User();
        owner.setId(1L);
        return new Venue(new DataRegisterVenue("Arena", "Quadras", "08:00", "22:00", 1,
                new DataAddress("Rua", "Centro", "12345678", "Cidade", "SP", null, "1")), owner);
    }

    @Test void startsPendingAndOwnerUpdatesCannotChangeApproval() {
        Venue venue = newVenue();
        assertEquals(VenueStatus.PENDING, new DataDetailVenue(venue).status());
        venue.approve();
        venue.updateInformation(new DataUpdateVenue(1L, "Novo nome", null, null, null, null, null));
        assertEquals(VenueStatus.APPROVED, new DataListVenue(venue).status());
    }

    @Test void adminListsPendingAndCanApproveOrReject() {
        login("ADMIN", 2L);
        Venue venue = newVenue();
        when(venues.findById(1L)).thenReturn(Optional.of(venue));
        when(venues.findAllByStatus(VenueStatus.PENDING, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(venue)));
        assertEquals(VenueStatus.PENDING, admin.list(VenueStatus.PENDING, Pageable.unpaged())
                .getBody().getContent().get(0).status());
        assertEquals(VenueStatus.APPROVED, admin.approve(1L).getBody().status());
        assertEquals(VenueStatus.REJECTED, admin.reject(1L).getBody().status());
    }

    @Test void nonAdminsCannotListApproveOrReject() {
        for (String role : List.of("OWNER", "PLAYER")) {
            login(role, 1L);
            assertThrows(AccessDeniedException.class, () -> admin.list(VenueStatus.PENDING, Pageable.unpaged()));
            assertThrows(AccessDeniedException.class, () -> admin.approve(1L));
            assertThrows(AccessDeniedException.class, () -> admin.reject(1L));
        }
        verifyNoInteractions(venues);
    }

    @Test void ownerSeesOwnStatusButCannotReadAnotherOwnersVenue() {
        var auth = login("OWNER", 1L);
        when(venues.findById(1L)).thenReturn(Optional.of(newVenue()));
        var response = ownerController.detail(1L, auth);
        assertEquals(VenueStatus.PENDING, ((DataDetailVenue) response.getBody()).status());
        var other = login("OWNER", 3L);
        assertThrows(RuntimeException.class, () -> ownerController.detail(1L, other));
    }

    @Test void missingVenueReturns404() {
        login("ADMIN", 2L);
        when(venues.findById(99L)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> admin.approve(99L))
                .getStatusCode().value());
    }
}
