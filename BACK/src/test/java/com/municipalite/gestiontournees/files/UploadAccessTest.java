package com.municipalite.gestiontournees.files;

import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.vision.repository.ImageAnalysisRepository;
import com.municipalite.gestiontournees.security.AccessService;
import com.municipalite.gestiontournees.tournee.model.Tournee;
import com.municipalite.gestiontournees.tournee.model.TourneeVisit;
import com.municipalite.gestiontournees.tournee.repository.TourneeRepository;
import com.municipalite.gestiontournees.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.access.AccessDeniedException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadAccessTest {
    @TempDir Path directory;

    @Test void onlyAssignedDriverMayReadBeforePhoto() throws Exception {
        var access = mock(AccessService.class);
        var tours = mock(TourneeRepository.class);
        var analyses = mock(ImageAnalysisRepository.class);
        var properties = new AiProperties();
        properties.getStorage().setPhotosDir(directory.toString());
        var controller = new UploadController(access, properties, tours, analyses);
        Files.write(directory.resolve("before.jpg"), new byte[]{1, 2, 3});
        var visit = new TourneeVisit(); visit.setBeforePhoto("/uploads/photos/before.jpg");
        when(tours.findAll()).thenReturn(List.of(Tournee.builder().id("t1").chauffeurId("driver-1").visits(List.of(visit)).build()));
        when(access.currentUser()).thenReturn(User.builder().id("driver-2").build());
        assertThrows(AccessDeniedException.class, () -> controller.read("/photos/before.jpg"));
        when(access.currentUser()).thenReturn(User.builder().id("driver-1").build());
        assertEquals(200, controller.read("/photos/before.jpg").getStatusCode().value());
        assertThrows(AccessDeniedException.class, () -> controller.read("/cv/private.pdf"));
    }

    @Test void pathTraversalIsRejectedEvenForSupervisor() {
        var controller = new UploadController(mock(AccessService.class), new AiProperties(),
                mock(TourneeRepository.class), mock(ImageAnalysisRepository.class));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.read("/photos/../../application.properties"));
    }
}
