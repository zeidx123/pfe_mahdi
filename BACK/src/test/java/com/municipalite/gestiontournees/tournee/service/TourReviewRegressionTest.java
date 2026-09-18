package com.municipalite.gestiontournees.tournee.service;

import com.municipalite.gestiontournees.ai.vision.model.ImageAnalysis;
import com.municipalite.gestiontournees.ai.vision.service.ImageAnalysisService;
import com.municipalite.gestiontournees.camion.repository.CamionRepository;
import com.municipalite.gestiontournees.pointcollecte.repository.PointCollecteRepository;
import com.municipalite.gestiontournees.security.AccessService;
import com.municipalite.gestiontournees.tournee.model.*;
import com.municipalite.gestiontournees.tournee.repository.TourneeRepository;
import com.municipalite.gestiontournees.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Regressions found by independent review of photo replacement and delayed AI responses. */
class TourReviewRegressionTest {
    @TempDir Path storage;
    private TourneeService service;
    private Tournee tour;
    private TourneeVisit visit;
    private ImageAnalysisService vision;

    @BeforeEach void setup() {
        var tours = mock(TourneeRepository.class);
        var access = mock(AccessService.class);
        vision = mock(ImageAnalysisService.class);
        service = new TourneeService(tours, mock(CamionRepository.class), mock(UserRepository.class),
                mock(PointCollecteRepository.class), mock(TourneeRouteOptimizationService.class), new RoutingProperties(), access, vision);
        visit = TourneeVisit.builder().pointId("point").status(TourneeVisit.Status.ARRIVED)
                .arrivedAt(LocalDateTime.now()).afterPhoto("old.jpg").build();
        tour = Tournee.builder().id("tour").chauffeurId("driver").statut(StatutTournee.EN_COURS)
                .pointsCollecteIds(List.of("point")).visits(new ArrayList<>(List.of(visit))).build();
        when(access.requireTourAccess("tour")).thenReturn(tour);
        when(tours.save(any())).thenAnswer(call -> call.getArgument(0));
        ReflectionTestUtils.setField(service, "photoDirectory", storage.toString());
    }

    @Test void delayedAnalysisCannotLabelReplacementPhotoClean() {
        when(vision.analyzeStoredEvidence(eq("/uploads/photos/old.jpg"), isNull(), eq("point"), eq("tour"), eq("driver")))
                .thenAnswer(call -> {
                    visit.setAfterPhoto("replacement.jpg"); // Simulates a new photo saved while the provider was working.
                    return ImageAnalysis.builder().id("old-analysis").status("CLEANED").build();
                });
        try { service.analyzeVisit("tour", "point"); }
        catch (ResponseStatusException expectedConflict) { assertEquals(409, expectedConflict.getStatusCode().value()); }
        assertNull(visit.getAnalysisId(), "The analysis of old.jpg must not attach to replacement.jpg");
        assertNull(visit.getAnalysisStatus());
    }

    @Test void replacingEvidenceClearsPriorHumanReview() throws Exception {
        visit.setReviewLabel("CLEANED"); visit.setReviewNotes("Old image reviewed");
        visit.setReviewedAt(LocalDateTime.now()); visit.setReviewedBy("supervisor");
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        service.savePointPhoto("tour", "point", new MockMultipartFile("photo", "new.png", "image/png", bytes.toByteArray()), "AFTER");
        assertNull(visit.getReviewLabel()); assertNull(visit.getReviewNotes());
        assertNull(visit.getReviewedAt()); assertNull(visit.getReviewedBy());
    }
}
