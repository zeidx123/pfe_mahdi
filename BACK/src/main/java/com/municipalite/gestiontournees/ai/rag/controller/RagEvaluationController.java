package com.municipalite.gestiontournees.ai.rag.controller;

import com.municipalite.gestiontournees.ai.rag.dto.*;
import com.municipalite.gestiontournees.ai.rag.service.RagEvaluationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/rag/evaluation")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERVISEUR')")
public class RagEvaluationController {
    private final RagEvaluationService evaluation;

    @GetMapping("/corpus")
    public RagEvaluationResponse.CorpusResponse corpus() { return evaluation.corpus(); }

    @PostMapping
    public RagEvaluationResponse evaluate(@Valid @RequestBody RagEvaluationRequest request) {
        return evaluation.evaluate(request);
    }
}
