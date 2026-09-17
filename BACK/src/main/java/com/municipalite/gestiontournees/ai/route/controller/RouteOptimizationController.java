package com.municipalite.gestiontournees.ai.route.controller;

import com.municipalite.gestiontournees.ai.route.dto.OptimizationRequest;
import com.municipalite.gestiontournees.ai.route.dto.OptimizationResponse;
import com.municipalite.gestiontournees.ai.route.model.RouteOptimization;
import com.municipalite.gestiontournees.ai.route.service.RouteOptimizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour l'Agent IA d'optimisation des tournées.
 * Tous les endpoints nécessitent le rôle SUPERVISEUR.
 */
@RestController
@RequestMapping("/api/ai/routes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERVISEUR')")
public class RouteOptimizationController {

    private final RouteOptimizationService routeOptimizationService;

    /**
     * POST /api/ai/routes/optimize
     * Calcule l'ordre optimal pour une liste de points de collecte et un camion.
     */
    @PostMapping("/optimize")
    public ResponseEntity<OptimizationResponse> optimizeRoute(@RequestBody OptimizationRequest request) {
        OptimizationResponse response = routeOptimizationService.optimize(request);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/ai/routes/history
     * Historique de toutes les optimisations.
     */
    @GetMapping("/history")
    public ResponseEntity<List<RouteOptimization>> getHistory() {
        return ResponseEntity.ok(routeOptimizationService.getAll());
    }

    /**
     * PATCH /api/ai/routes/{id}/status
     * Le superviseur valide ou rejette une proposition d'optimisation.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<RouteOptimization> updateStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> payload) {
        String statut = payload.get("statut");
        return ResponseEntity.ok(routeOptimizationService.validateOptimization(id, statut));
    }
}
