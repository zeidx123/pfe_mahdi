package com.municipalite.gestiontournees.reclamation;

import com.municipalite.gestiontournees.reclamation.model.Reclamation;
import com.municipalite.gestiontournees.reclamation.repository.ReclamationRepository;
import com.municipalite.gestiontournees.reclamation.service.ReclamationService;
import com.municipalite.gestiontournees.user.model.User;
import com.municipalite.gestiontournees.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReclamationValidationTest {
    private final ReclamationRepository complaints = mock(ReclamationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ReclamationService service = new ReclamationService(complaints, users);

    @Test void emptyComplaintAndReplyAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.createReclamation(new Reclamation(), "test"));
        assertThrows(IllegalArgumentException.class, () -> service.repondreReclamation("id", "  "));
        verifyNoInteractions(complaints, users);
    }

    @Test void clientCannotOverwriteExistingComplaintOrSpoofDriver() {
        when(users.findByEmail("driver@test.invalid")).thenReturn(Optional.of(User.builder().id("driver-1").build()));
        when(complaints.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var input = Reclamation.builder().id("other-complaint").chauffeurId("other-driver")
                .titre("Obstacle").type("ROUTE_BLOQUEE").description("Accès bloqué").build();
        var saved = service.createReclamation(input, "driver@test.invalid");
        assertNull(saved.getId());
        assertEquals("driver-1", saved.getChauffeurId());
        assertEquals("EN_ATTENTE", saved.getStatut());
    }
}
