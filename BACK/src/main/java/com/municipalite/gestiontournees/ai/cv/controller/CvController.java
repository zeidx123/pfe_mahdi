package com.municipalite.gestiontournees.ai.cv.controller;

import com.municipalite.gestiontournees.ai.cv.model.Candidate;
import com.municipalite.gestiontournees.ai.cv.service.CvAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour l'Agent IA de traitement des CV.
 * Tous les endpoints sont réservés au SUPERVISEUR.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERVISEUR')")
public class CvController {

    private final CvAnalysisService cvAnalysisService;

    /**
     * Upload et analyse automatique d'un CV PDF.
     * POST /api/ai/cv/upload
     */
    @PostMapping(value = "/cv/upload", consumes = "multipart/form-data")
    public ResponseEntity<Candidate> uploadAndAnalyze(
            @RequestParam("cv") MultipartFile file) {
        Candidate candidate = cvAnalysisService.analyzeAndSaveCv(file);
        return ResponseEntity.ok(candidate);
    }

    /**
     * Liste de tous les candidats.
     * GET /api/ai/candidates
     */
    @GetMapping("/candidates")
    public ResponseEntity<List<Candidate>> getAllCandidates() {
        return ResponseEntity.ok(cvAnalysisService.getAllCandidates());
    }

    /**
     * Détail d'un candidat.
     * GET /api/ai/candidates/{id}
     */
    @GetMapping("/candidates/{id}")
    public ResponseEntity<Candidate> getCandidate(@PathVariable String id) {
        return ResponseEntity.ok(cvAnalysisService.getCandidateById(id));
    }

    /**
     * Le superviseur enregistre sa décision (Accepter / Rejeter / En entretien).
     * PATCH /api/ai/candidates/{id}/decision
     */
    @PatchMapping("/candidates/{id}/decision")
    public ResponseEntity<Candidate> updateDecision(
            @PathVariable String id,
            @RequestBody Map<String, String> payload) {
        String statut = payload.get("statut");
        String notes = payload.get("notesSuperviseur");
        return ResponseEntity.ok(cvAnalysisService.updateDecision(id, statut, notes));
    }
}
