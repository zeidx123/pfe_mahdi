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
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Regressions found by independent review of photo replacement and delayed AI responses. */
class TourReviewRegressionTest {
    @TempDir Path storage;
    private TourneeService service;
    private Tournee tour;
    private TourneeVisit visit;
    private ImageAnalysisService vision;
    private AccessService access;
    private TourneeRepository tours;

    @BeforeEach void setup() {
        tours = mock(TourneeRepository.class);
        access = mock(AccessService.class);
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

    @Test void savedAssessmentReturnsWithoutAnotherProviderCallOrChangingHumanReview() {
        visit.setAnalysisId("saved"); visit.setAnalysisStatus("NOT_CLEANED");
        visit.setReviewLabel("CLEANED"); visit.setReviewNotes("Human review");
        when(vision.reusableVisitAnalysis("saved", "/uploads/photos/old.jpg", null, "point", "tour", "driver"))
                .thenReturn(Optional.of(ImageAnalysis.builder().id("saved").status("NOT_CLEANED").build()));
        var first = service.analyzeVisit("tour", "point");
        var second = service.analyzeVisit("tour", "point");
        assertEquals("saved", first.getVisits().get(0).getAnalysisId());
        assertEquals("NOT_CLEANED", second.getVisits().get(0).getAnalysisStatus());
        assertEquals("CLEANED", visit.getReviewLabel());
        assertEquals("Human review", visit.getReviewNotes());
        verify(vision, never()).analyzeStoredEvidence(anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test void unavailableAssessmentCanBeRetriedAndGuardIsReleased() {
        when(vision.analyzeStoredEvidence(anyString(), isNull(), eq("point"), eq("tour"), eq("driver")))
                .thenReturn(ImageAnalysis.builder().id("failure").status("ANALYSIS_UNAVAILABLE").build(),
                        ImageAnalysis.builder().id("success").status("NOT_CLEANED").build());
        assertEquals("ANALYSIS_UNAVAILABLE", service.analyzeVisit("tour", "point").getVisits().get(0).getAnalysisStatus());
        assertEquals("NOT_CLEANED", service.analyzeVisit("tour", "point").getVisits().get(0).getAnalysisStatus());
        verify(vision, times(2)).analyzeStoredEvidence(anyString(), isNull(), eq("point"), eq("tour"), eq("driver"));
    }

    @Test void cachedResultCannotOverwriteEvidenceReplacedDuringLookup() {
        visit.setAnalysisId("saved"); visit.setAnalysisStatus(null);
        visit.setReviewLabel("CLEANED"); visit.setReviewNotes("Old evidence");
        var replacementVisit = TourneeVisit.builder().pointId("point").afterPhoto("replacement.jpg")
                .status(TourneeVisit.Status.ARRIVED).build();
        var replacementTour = Tournee.builder().id("tour").chauffeurId("driver").statut(StatutTournee.EN_COURS)
                .pointsCollecteIds(List.of("point")).visits(new ArrayList<>(List.of(replacementVisit))).build();
        when(vision.reusableVisitAnalysis("saved", "/uploads/photos/old.jpg", null, "point", "tour", "driver"))
                .thenAnswer(call -> {
                    when(access.requireTourAccess("tour")).thenReturn(replacementTour);
                    return Optional.of(ImageAnalysis.builder().id("saved").status("CLEANED").build());
                });
        var conflict = assertThrows(ResponseStatusException.class, () -> service.analyzeVisit("tour", "point"));
        assertEquals(409, conflict.getStatusCode().value());
        assertEquals("replacement.jpg", replacementVisit.getAfterPhoto());
        assertNull(replacementVisit.getAnalysisId());
        assertNull(replacementVisit.getReviewLabel());
        verify(tours, never()).save(any());
        verify(vision, never()).analyzeStoredEvidence(anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test void overlappingRequestsForOneVisitMakeOnlyOneProviderCall() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        when(vision.analyzeStoredEvidence(anyString(), isNull(), eq("point"), eq("tour"), eq("driver")))
                .thenAnswer(call -> {
                    entered.countDown();
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                    return ImageAnalysis.builder().id("single-call").status("CLEANED").build();
                });
        try {
            var first = executor.submit(() -> service.analyzeVisit("tour", "point"));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var conflict = assertThrows(ResponseStatusException.class, () -> service.analyzeVisit("tour", "point"));
            assertEquals(409, conflict.getStatusCode().value());
            assertTrue(conflict.getReason().contains("déjà en cours"));
            release.countDown();
            assertEquals("single-call", first.get(5, TimeUnit.SECONDS).getVisits().get(0).getAnalysisId());
            verify(vision).analyzeStoredEvidence(anyString(), isNull(), eq("point"), eq("tour"), eq("driver"));
        } finally {
            release.countDown(); executor.shutdownNow();
        }
    }
}
