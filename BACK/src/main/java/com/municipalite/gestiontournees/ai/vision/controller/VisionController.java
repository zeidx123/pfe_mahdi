package com.municipalite.gestiontournees.ai.vision.controller;

import com.municipalite.gestiontournees.ai.vision.model.AiProblem;
import com.municipalite.gestiontournees.ai.vision.model.ImageAnalysis;
import com.municipalite.gestiontournees.ai.vision.service.ImageAnalysisService;
import com.municipalite.gestiontournees.security.AccessService;
import com.municipalite.gestiontournees.tournee.model.Tournee;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class VisionController {
    private final ImageAnalysisService imageAnalysisService;
    private final AccessService accessService;

    @GetMapping("/vision/evaluation-config")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public ImageAnalysisService.EvaluationConfiguration evaluationConfiguration() {
        accessService.requireSupervisor();
        return imageAnalysisService.evaluationConfiguration();
    }

    @PostMapping(value = "/vision/evaluate", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public ImageAnalysis evaluate(@RequestParam("afterPhoto") MultipartFile afterPhoto,
            @RequestParam(value = "beforePhoto", required = false) MultipartFile beforePhoto,
            @RequestParam("expectedModel") String expectedModel,
            @RequestParam("expectedPromptVersion") String expectedPromptVersion) {
        accessService.requireSupervisor();
        return imageAnalysisService.evaluateEvidence(afterPhoto, beforePhoto, expectedModel, expectedPromptVersion);
    }

    @PostMapping(value = "/vision/analyze", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('SUPERVISEUR', 'CHAUFFEUR')")
    public ResponseEntity<ImageAnalysis> analyzePhoto(@RequestParam("photo") MultipartFile photo,
            @RequestParam("pointCollecteId") String pointCollecteId, @RequestParam("tourneeId") String tourneeId,
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude) {
        Tournee tour = accessService.requireTourAccess(tourneeId);
        if (tour.getPointsCollecteIds() == null || !tour.getPointsCollecteIds().contains(pointCollecteId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce point n'appartient pas à la tournée.");
        }
        return ResponseEntity.ok(imageAnalysisService.analyzeImage(photo, pointCollecteId, tourneeId,
                tour.getChauffeurId(), latitude, longitude));
    }

    @GetMapping("/vision/analyses/{id}")
    @PreAuthorize("isAuthenticated()")
    public ImageAnalysis getAnalysis(@PathVariable String id) {
        ImageAnalysis analysis = imageAnalysisService.getAnalysis(id);
        checkAccess(analysis);
        return analysis;
    }

    public record Confirmation(Boolean validee, String correctionChauffeur) {}
    @PatchMapping("/vision/confirm/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISEUR', 'CHAUFFEUR')")
    public ImageAnalysis confirmPrediction(@PathVariable String id, @RequestBody Confirmation payload) {
        checkAccess(imageAnalysisService.getAnalysis(id));
        return imageAnalysisService.confirmOrCorrect(id, payload.validee(), payload.correctionChauffeur());
    }

    public record Review(String label, String notes) {}
    @PatchMapping("/vision/analyses/{id}/review")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public ImageAnalysis review(@PathVariable String id, @RequestBody Review payload) {
        accessService.requireSupervisor();
        return imageAnalysisService.review(id, payload.label(), payload.notes(), accessService.currentUser().getId());
    }

    private void checkAccess(ImageAnalysis analysis) {
        if (analysis.getTourneeId() == null) accessService.requireSupervisor();
        else accessService.requireTourAccess(analysis.getTourneeId());
    }

    @GetMapping("/vision/analyses")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public List<ImageAnalysis> getAllAnalyses() { return imageAnalysisService.getAllAnalyses(); }

    @GetMapping("/problems")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public List<AiProblem> getAllProblems() { return imageAnalysisService.getAllProblems(); }
}
