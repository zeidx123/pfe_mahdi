package com.municipalite.gestiontournees.ai.vision.controller;

import com.municipalite.gestiontournees.ai.vision.model.ImageAnalysis;
import com.municipalite.gestiontournees.ai.vision.narrative.NarrativeService;
import com.municipalite.gestiontournees.ai.vision.service.ImageAnalysisService;
import com.municipalite.gestiontournees.security.AccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Optional supervisor summary of a LOCAL analysis. Kept apart from VisionController so that its
 * existing @WebMvcTest needs no new mock; same access rule as GET /vision/analyses/{id}.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class VisionNarrativeController {
    private final ImageAnalysisService imageAnalysisService;
    private final NarrativeService narrativeService;
    private final AccessService accessService;

    @PostMapping("/vision/analyses/{id}/narrative")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public ImageAnalysis narrative(@PathVariable String id) {
        accessService.requireSupervisor();
        ImageAnalysis analysis = imageAnalysisService.getAnalysis(id);
        if (analysis.getTourneeId() == null) accessService.requireSupervisor();
        else accessService.requireTourAccess(analysis.getTourneeId());
        return narrativeService.narrate(id);
    }
}
