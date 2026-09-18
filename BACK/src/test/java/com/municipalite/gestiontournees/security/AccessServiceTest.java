package com.municipalite.gestiontournees.security;

import com.municipalite.gestiontournees.tournee.model.Tournee;
import com.municipalite.gestiontournees.tournee.repository.TourneeRepository;
import com.municipalite.gestiontournees.user.model.Role;
import com.municipalite.gestiontournees.user.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AccessServiceTest {
    private final AccessService access = new AccessService(mock(TourneeRepository.class));
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void driverCanOnlyAccessAssignedTour() {
        var user = User.builder().id("driver-1").email("driver@test.invalid").role(Role.CHAUFFEUR).actif(true).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        assertDoesNotThrow(() -> access.requireTourAccess(Tournee.builder().chauffeurId("driver-1").build()));
        assertThrows(AccessDeniedException.class, () -> access.requireTourAccess(Tournee.builder().chauffeurId("driver-2").build()));
    }

    @Test void previouslyIssuedTokenIsInvalidForDisabledAccount() {
        var user = User.builder().email("driver@test.invalid").role(Role.CHAUFFEUR).actif(true).build();
        var tokens = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokens, "jwtSecret", Base64.getEncoder().encodeToString(new byte[32]));
        ReflectionTestUtils.setField(tokens, "jwtExpiration", 60000L);
        String token = tokens.generateToken(user);
        assertTrue(tokens.isTokenValid(token, user));
        user.setActif(false);
        assertFalse(tokens.isTokenValid(token, user));
    }
}
