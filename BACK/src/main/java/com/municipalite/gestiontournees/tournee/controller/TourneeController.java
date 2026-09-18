package com.municipalite.gestiontournees.tournee.controller;

import com.municipalite.gestiontournees.tournee.dto.TourneeRequest;
import com.municipalite.gestiontournees.tournee.dto.TourneeResponse;
import com.municipalite.gestiontournees.tournee.dto.VisitCompletionRequest;
import com.municipalite.gestiontournees.tournee.dto.VisitReviewRequest;
import com.municipalite.gestiontournees.tournee.dto.FinishTourRequest;
import com.municipalite.gestiontournees.tournee.model.StatutTournee;
import com.municipalite.gestiontournees.tournee.service.TourneeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tournees")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPERVISEUR', 'CHAUFFEUR')")
public class TourneeController {

    private final TourneeService tourneeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERVISEUR', 'CHAUFFEUR', 'AGENT')")
    public ResponseEntity<List<TourneeResponse>> getAll() {
        return ResponseEntity.ok(tourneeService.getAll());
    }

    // Endpoint pour l'application mobile — accessible par les chauffeurs
    @GetMapping("/mes-tournees")
    @PreAuthorize("hasAnyRole('SUPERVISEUR', 'CHAUFFEUR', 'AGENT')")
    public ResponseEntity<List<TourneeResponse>> getMesTournees(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(tourneeService.getTourneesByAgent(userDetails.getUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TourneeResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(tourneeService.getById(id));
    }

    @GetMapping("/{id}/route-export")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public com.municipalite.gestiontournees.tournee.model.RoadRoute routeExport(@PathVariable String id) {
        var route = tourneeService.getById(id).getRoute();
        if (route == null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, "Aucun itinéraire routier calculé");
        return route;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public ResponseEntity<TourneeResponse> create(@Valid @RequestBody TourneeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tourneeService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public ResponseEntity<TourneeResponse> update(
            @PathVariable String id,
            @Valid @RequestBody TourneeRequest request) {
        return ResponseEntity.ok(tourneeService.update(id, request));
    }

    @PatchMapping("/{id}/statut")
    public ResponseEntity<TourneeResponse> updateStatut(
            @PathVariable String id,
            @RequestParam StatutTournee statut) {
        return ResponseEntity.ok(tourneeService.updateStatut(id, statut));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        tourneeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/points/{pointId}/photo", consumes = "multipart/form-data")
    public ResponseEntity<TourneeResponse> uploadPointPhoto(
            @PathVariable String id,
            @PathVariable String pointId,
            @RequestParam("photo") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(defaultValue = "AFTER") String stage) {
        return ResponseEntity.ok(tourneeService.savePointPhoto(id, pointId, file, stage));
    }

    @PostMapping(value = "/{id}/fin/photo", consumes = "multipart/form-data")
    public ResponseEntity<TourneeResponse> uploadFinPhoto(
            @PathVariable String id,
            @RequestParam("photo") org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.ok(tourneeService.saveFinPhoto(id, file));
    }

    /**
     * Déclenche manuellement le recalcul de l'optimisation IA pour une tournée.
     */
    @PostMapping("/{id}/optimiser")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public ResponseEntity<TourneeResponse> reoptimiser(@PathVariable String id) {
        return ResponseEntity.ok(tourneeService.reoptimiser(id));
    }

    @PostMapping("/{id}/start")
    public TourneeResponse start(@PathVariable String id) { return tourneeService.start(id); }

    @PostMapping("/{id}/finish")
    public TourneeResponse finish(@PathVariable String id, @RequestBody FinishTourRequest request) {
        return tourneeService.finish(id, request.isReturnedToDepot());
    }

    @PostMapping("/{id}/points/{pointId}/arrive")
    public TourneeResponse arrive(@PathVariable String id, @PathVariable String pointId) {
        return tourneeService.arrive(id, pointId);
    }

    @PostMapping("/{id}/points/{pointId}/complete")
    public TourneeResponse complete(@PathVariable String id, @PathVariable String pointId,
                                  @Valid @RequestBody VisitCompletionRequest request) {
        return tourneeService.completeVisit(id, pointId, request);
    }

    @PostMapping("/{id}/points/{pointId}/analyse")
    public TourneeResponse analyze(@PathVariable String id, @PathVariable String pointId) {
        return tourneeService.analyzeVisit(id, pointId);
    }

    @PatchMapping("/{id}/points/{pointId}/review")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public TourneeResponse review(@PathVariable String id, @PathVariable String pointId,
                                @Valid @RequestBody VisitReviewRequest request) {
        return tourneeService.reviewVisit(id, pointId, request);
    }
}
