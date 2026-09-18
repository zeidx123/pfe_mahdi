package com.municipalite.gestiontournees;

import com.municipalite.gestiontournees.user.model.Role;
import com.municipalite.gestiontournees.user.model.User;
import com.municipalite.gestiontournees.user.repository.UserRepository;
import com.municipalite.gestiontournees.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class UserDiagnosticTest {
    @Test
    void inactiveDriverCannotAuthenticateByMatricule() {
        var users = mock(UserRepository.class);
        var driver = User.builder().email("driver@test.invalid").matricule("D-001")
                .password("encoded").role(Role.CHAUFFEUR).actif(false).build();
        when(users.findByEmail("D-001")).thenReturn(Optional.empty());
        when(users.findByMatricule("D-001")).thenReturn(Optional.of(driver));
        var provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(new UserService(users));
        provider.setPasswordEncoder(mock(PasswordEncoder.class));
        assertThrows(DisabledException.class, () -> provider.authenticate(
                new UsernamePasswordAuthenticationToken("D-001", "irrelevant")));
        verify(users, never()).save(any());
    }
}
