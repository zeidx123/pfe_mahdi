package com.municipalite.gestiontournees.tracking;

import com.municipalite.gestiontournees.camion.repository.CamionRepository;
import com.municipalite.gestiontournees.security.AccessService;
import com.municipalite.gestiontournees.tournee.model.StatutTournee;
import com.municipalite.gestiontournees.tournee.model.Tournee;
import com.municipalite.gestiontournees.tournee.repository.TourneeRepository;
import com.municipalite.gestiontournees.tracking.controller.TrackingController;
import com.municipalite.gestiontournees.tracking.model.VehiculePosition;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrackingOwnershipTest {
    private final AccessService access = mock(AccessService.class);
    private final SimpMessagingTemplate messages = mock(SimpMessagingTemplate.class);
    private final TrackingController controller = new TrackingController(messages, access,
            mock(TourneeRepository.class), mock(CamionRepository.class));

    @Test void unauthorizedAndWrongTruckUpdatesAreNotBroadcast() {
        var position = VehiculePosition.builder().tourneeId("t1").camionId("c2").latitude(36.8).longitude(10.1).build();
        when(access.requireTourAccess("t1")).thenThrow(new AccessDeniedException("not assigned"));
        assertThrows(AccessDeniedException.class, () -> controller.receivePosition(position));
        reset(access);
        when(access.requireTourAccess("t1")).thenReturn(Tournee.builder().camionId("c1").statut(StatutTournee.EN_COURS).build());
        assertThrows(IllegalArgumentException.class, () -> controller.receivePosition(position));
        verifyNoInteractions(messages);
    }

    @Test void completedTourCannotKeepPublishingPositions() {
        when(access.requireTourAccess("t1")).thenReturn(Tournee.builder().camionId("c1").statut(StatutTournee.TERMINEE).build());
        assertThrows(IllegalArgumentException.class, () -> controller.receivePosition(
                VehiculePosition.builder().tourneeId("t1").camionId("c1").latitude(36.8).longitude(10.1).build()));
        verifyNoInteractions(messages);
    }
}
