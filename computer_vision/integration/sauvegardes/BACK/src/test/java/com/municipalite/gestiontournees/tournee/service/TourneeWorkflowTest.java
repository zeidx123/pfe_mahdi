package com.municipalite.gestiontournees.tournee.service;

import com.municipalite.gestiontournees.ai.vision.model.ImageAnalysis;
import com.municipalite.gestiontournees.ai.vision.service.ImageAnalysisService;
import com.municipalite.gestiontournees.camion.model.*;
import com.municipalite.gestiontournees.camion.repository.CamionRepository;
import com.municipalite.gestiontournees.pointcollecte.model.PointCollecte;
import com.municipalite.gestiontournees.pointcollecte.repository.PointCollecteRepository;
import com.municipalite.gestiontournees.security.AccessService;
import com.municipalite.gestiontournees.tournee.dto.*;
import com.municipalite.gestiontournees.tournee.model.*;
import com.municipalite.gestiontournees.tournee.repository.TourneeRepository;
import com.municipalite.gestiontournees.user.model.*;
import com.municipalite.gestiontournees.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TourneeWorkflowTest {
    TourneeRepository tours; CamionRepository trucks; UserRepository users; PointCollecteRepository points;
    AccessService access; ImageAnalysisService vision; TourneeRouteOptimizationService optimizer;
    TourneeService service; Tournee tour; Camion truck;
    @BeforeEach void setup() {
        tours = mock(TourneeRepository.class); trucks = mock(CamionRepository.class); users = mock(UserRepository.class);
        points = mock(PointCollecteRepository.class); access = mock(AccessService.class); vision = mock(ImageAnalysisService.class);
        optimizer = mock(TourneeRouteOptimizationService.class);
        service = new TourneeService(tours,trucks,users,points,optimizer,new RoutingProperties(),access,vision);
        tour = Tournee.builder().id("tour").reference("T1").camionId("truck").chauffeurId("driver")
                .statut(StatutTournee.PLANIFIEE).pointsCollecteIds(List.of("point"))
                .route(RoadRoute.builder().orderedPointIds(List.of("point")).totalSeconds(600).serviceSeconds(300)
                        .matrixPointIds(List.of("DEPOT","point")).matrixCoordinates(List.of(List.of(10.283535,36.722857),List.of(10.1,36.8))).build())
                .optimisationEffectuee(true).build();
        truck = Camion.builder().id("truck").actif(true).statut(StatutCamion.DISPONIBLE).build();
        User driver = User.builder().id("driver").role(Role.CHAUFFEUR).actif(true).build();
        when(access.requireTourAccess("tour")).thenAnswer(i -> tour);
        when(access.currentUser()).thenReturn(driver);
        when(tours.save(any())).thenAnswer(i -> i.getArgument(0)); when(tours.findAll()).thenReturn(List.of(tour));
        when(trucks.findById("truck")).thenReturn(Optional.of(truck)); when(users.findById("driver")).thenReturn(Optional.of(driver));
        when(points.findAllById(any())).thenReturn(List.of(PointCollecte.builder().id("point").actif(true).latitude(36.8).longitude(10.1).build()));
    }
    VisitCompletionRequest completion(VisitCompletionRequest.Outcome outcome, String notes) {
        VisitCompletionRequest r = new VisitCompletionRequest(); r.setOutcome(outcome); r.setNotes(notes); return r;
    }
    @Test void startAndResumeKeepStartTimestampAndTruckState() {
        var started=service.start("tour"); LocalDateTime time=started.getHeureDebutReelle();
        assertEquals(StatutCamion.EN_TOURNEE,truck.getStatut()); assertEquals(600.,started.getEstimatedAtStartSeconds());
        assertEquals(time,service.start("tour").getHeureDebutReelle()); assertEquals(time,service.getById("tour").getHeureDebutReelle());
        verify(trucks,times(1)).save(truck);
    }
    @Test void normalCompletionRequiresArrivalAndAfterPhoto() {
        service.start("tour");
        assertThrows(ResponseStatusException.class,() -> service.completeVisit("tour","point",completion(VisitCompletionRequest.Outcome.COMPLETED,null)));
        service.arrive("tour","point"); tour.getVisits().get(0).setBeforePhoto("before.jpg");
        assertThrows(ResponseStatusException.class,() -> service.completeVisit("tour","point",completion(VisitCompletionRequest.Outcome.COMPLETED,null)));
        tour.getVisits().get(0).setAfterPhoto("after.jpg");
        var result=service.completeVisit("tour","point",completion(VisitCompletionRequest.Outcome.COMPLETED,null));
        assertEquals(TourneeVisit.Status.COMPLETED_REPORTED,result.getVisits().get(0).getStatus());
    }
    @Test void obstructionWithoutSafePhotoRequiresReasonAndCanFinish() {
        service.start("tour");
        assertThrows(IllegalArgumentException.class,() -> service.completeVisit("tour","point",completion(VisitCompletionRequest.Outcome.ISSUE," ")));
        service.completeVisit("tour","point",completion(VisitCompletionRequest.Outcome.ISSUE,"Accès dangereux, photo impossible"));
        assertThrows(IllegalArgumentException.class,() -> service.finish("tour",false));
        var ended=service.finish("tour",true);
        assertEquals(StatutTournee.TERMINEE,ended.getStatut()); assertEquals(StatutCamion.DISPONIBLE,truck.getStatut());
        assertNull(ended.getPhotoFin()); assertNull(ended.getObservedServiceSeconds()); assertNotNull(ended.getActualDurationSeconds());
    }
    @Test void cannotFinishWithPendingStopsOrRestartFinishedTour() {
        service.start("tour"); assertThrows(ResponseStatusException.class,() -> service.finish("tour",true));
        service.completeVisit("tour","point",completion(VisitCompletionRequest.Outcome.ISSUE,"Obstruction")); service.finish("tour",true);
        assertThrows(ResponseStatusException.class,() -> service.start("tour"));
        assertThrows(ResponseStatusException.class,() -> service.updateStatut("tour",StatutTournee.PLANIFIEE));
    }
    @Test void outageDoesNotBecomeCleanAndDoesNotBlockReportedCompletion() {
        service.start("tour"); service.arrive("tour","point"); tour.getVisits().get(0).setAfterPhoto("after.jpg");
        when(vision.analyzeStoredEvidence(anyString(),isNull(),eq("point"),eq("tour"),eq("driver")))
                .thenReturn(ImageAnalysis.builder().id("analysis").status("ANALYSIS_UNAVAILABLE").build());
        assertEquals("ANALYSIS_UNAVAILABLE",service.analyzeVisit("tour","point").getVisits().get(0).getAnalysisStatus());
        service.completeVisit("tour","point",completion(VisitCompletionRequest.Outcome.COMPLETED,null));
        assertEquals("ANALYSIS_UNAVAILABLE",service.finish("tour",true).getVisits().get(0).getAnalysisStatus());
    }
    @Test void unknownPointAndOutOfOrderArrivalAreRejected() {
        tour.setPointsCollecteIds(List.of("point","other")); tour.getRoute().setOrderedPointIds(List.of("point","other"));
        tour.setStatut(StatutTournee.EN_COURS);
        assertThrows(IllegalArgumentException.class,() -> service.arrive("tour","foreign"));
        assertThrows(ResponseStatusException.class,() -> service.arrive("tour","other"));
    }
    @Test void missingRoadRouteCannotStart() {
        tour.setRoute(null); assertThrows(ResponseStatusException.class,() -> service.start("tour"));
        assertNull(tour.getHeureDebutReelle());
    }
    @Test void optimizationFailureClearsPreviousEstimateAndIsVisible() {
        when(optimizer.optimize(anyList())).thenThrow(new IllegalArgumentException("GPS invalides"));
        assertThrows(IllegalArgumentException.class,() -> service.reoptimiser("tour"));
        assertNull(tour.getRoute()); assertNull(tour.getDistanceOptimaleKm()); assertFalse(tour.getOptimisationEffectuee());
        assertEquals("GPS invalides",tour.getRouteError());
    }
    @Test void existingDriverOrTruckInUseCannotStartAnotherTour() {
        Tournee active=Tournee.builder().id("other").chauffeurId("driver").statut(StatutTournee.EN_COURS).build();
        when(tours.findAll()).thenReturn(List.of(tour,active)); assertThrows(ResponseStatusException.class,() -> service.start("tour"));
        assertEquals(StatutTournee.PLANIFIEE,tour.getStatut());
    }
    @Test void coordinateChangeBeforeStartRequiresReoptimization() {
        PointCollecte point = points.findAllById(List.of("point")).get(0);
        point.setLatitude(37.0);
        assertThrows(ResponseStatusException.class, () -> service.start("tour"));
        assertEquals(StatutTournee.PLANIFIEE, tour.getStatut());
    }
    @Test void activeTourNavigationUsesItsSavedCoordinates() {
        service.start("tour");
        points.findAllById(List.of("point")).get(0).setLatitude(37.0);
        assertEquals(36.8, service.getById("tour").getPointsCollecte().get(0).getLatitude());
    }
    @Test void historicalPhotoDoesNotInventAfterEvidenceOrProgress() {
        tour.setStatut(StatutTournee.TERMINEE);
        tour.setPhotosPoints(Map.of("point", "historical.jpg"));
        var visit = service.getById("tour").getVisits().get(0);
        assertTrue(visit.isLegacyProgressMissing());
        assertEquals("historical.jpg", visit.getLegacyPhoto());
        assertNull(visit.getAfterPhoto()); assertNull(visit.getArrivedAt()); assertNull(visit.getCompletedAt());
        assertNull(visit.getAnalysisStatus()); assertFalse(visit.handled());
        // An old active visit still needs a newly recorded AFTER photo for a new normal completion.
        tour.setStatut(StatutTournee.EN_COURS);
        service.arrive("tour", "point");
        assertFalse(visit.isLegacyProgressMissing());
        assertThrows(ResponseStatusException.class, () -> service.completeVisit("tour", "point", completion(VisitCompletionRequest.Outcome.COMPLETED, null)));
    }
}
