package com.municipalite.gestiontournees;

import com.municipalite.gestiontournees.user.model.User;
import com.municipalite.gestiontournees.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** Startup regression tests deliberately use no Mongo server or email provider. */
class GestionTourneesApplicationTests {
    @Test
    void explicitBootstrapNeverResetsExistingAccounts() throws Exception {
        var users = mock(UserRepository.class);
        var encoder = mock(PasswordEncoder.class);
        when(users.findByEmail("supervisor@test.invalid")).thenReturn(Optional.of(new User()));
        new GestionTourneesApplication().dataInitializer(users, encoder,
                "supervisor@test.invalid", "test-password-only").run();
        verify(users).findByEmail("supervisor@test.invalid");
        verifyNoMoreInteractions(users);
        verifyNoInteractions(encoder);
    }

    @Test
    void explicitBootstrapRequiresConfiguredCredentialsBeforeDatabaseAccess() {
        var users = mock(UserRepository.class);
        var initializer = new GestionTourneesApplication().dataInitializer(users, mock(PasswordEncoder.class), "", "");
        assertThrows(IllegalArgumentException.class, initializer::run);
        verifyNoInteractions(users);
    }
}
