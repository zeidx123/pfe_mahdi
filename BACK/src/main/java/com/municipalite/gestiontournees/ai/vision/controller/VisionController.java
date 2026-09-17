package com.municipalite.gestiontournees.ai.vision.controller;

import com.municipalite.gestiontournees.ai.vision.model.AiProblem;
import com.municipalite.gestiontournees.ai.vision.model.ImageAnalysis;
import com.municipalite.gestiontournees.ai.vision.service.ImageAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour l'Agent IA d'Analyse Vision des Photos des points de collecte.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class VisionController {

    private final ImageAnalysisService imageAnalysisService;

    /**
     * POST /api/ai/vision/analyze
     * Permet au chauffeur d'envoyer la photo d'un point de collecte pour analyse IA.
     */
    @PostMapping(value = "/vision/analyze", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('SUPERVISEUR', 'CHAUFFEUR', 'AGENT')")
    public ResponseEntity<ImageAnalysis> analyzePhoto(
            @RequestParam("photo") MultipartFile photo,
            @RequestParam("pointCollecteId") String pointCollecteId,
            @RequestParam(value = "tourneeId", required = false) String tourneeId,
            @RequestParam(value = "chauffeurId", required = false) String chauffeurId,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude) {

        ImageAnalysis analysis = imageAnalysisService.analyzeImage(
                photo, pointCollecteId, tourneeId, chauffeurId, latitude, longitude);
        return ResponseEntity.ok(analysis);
    }

    /**
     * PATCH /api/ai/vision/confirm/{id}
     * Permet au chauffeur de confirmer ou corriger la prédiction de l'IA.
     */
    @PatchMapping("/vision/confirm/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISEUR', 'CHAUFFEUR', 'AGENT')")
    public ResponseEntity<ImageAnalysis> confirmPrediction(
            @PathVariable String id,
            @RequestBody Map<String, Object> payload) {
        Boolean validee = (Boolean) payload.get("validee");
        String correction = (String) payload.get("correctionChauffeur");
        return ResponseEntity.ok(imageAnalysisService.confirmOrCorrect(id, validee, correction));
    }

    /**
     * GET /api/ai/vision/analyses
     * Liste toutes les analyses d'images. (Superviseur)
     */
    @GetMapping("/vision/analyses")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public ResponseEntity<List<ImageAnalysis>> getAllAnalyses() {
        return ResponseEntity.ok(imageAnalysisService.getAllAnalyses());
    }

    /**
     * GET /api/ai/problems
     * Liste tous les problèmes créés automatiquement par l'IA. (Superviseur)
     */
    @GetMapping("/problems")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public ResponseEntity<List<AiProblem>> getAllProblems() {
        return ResponseEntity.ok(imageAnalysisService.getAllProblems());
    }
}
