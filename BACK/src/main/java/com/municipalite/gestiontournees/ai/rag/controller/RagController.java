package com.municipalite.gestiontournees.ai.rag.controller;

import com.municipalite.gestiontournees.ai.rag.dto.RagQueryRequest;
import com.municipalite.gestiontournees.ai.rag.dto.RagResponse;
import com.municipalite.gestiontournees.ai.rag.model.RagConversation;
import com.municipalite.gestiontournees.ai.rag.service.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Contrôleur REST pour l'Assistant RAG SmartWaste.
 * Accessible à tous les rôles authentifiés (Superviseur, Admin, Chauffeur).
 */
@RestController
@RequestMapping("/api/ai/rag")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class RagController {

    private final RagService ragService;

    /**
     * POST /api/ai/rag/query
     * Poser une question à l'Assistant RAG.
     */
    @PostMapping("/query")
    public ResponseEntity<RagResponse> query(
            @Valid @RequestBody RagQueryRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        RagResponse response = ragService.query(request, username);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/ai/rag/conversations
     * Obtenir les conversations RAG archivées de l'utilisateur connecté.
     */
    @GetMapping("/conversations")
    public ResponseEntity<List<RagConversation>> getConversations(Authentication authentication) {
        String username = authentication.getName();
        List<RagConversation> conversations = ragService.getUserConversations(username);
        return ResponseEntity.ok(conversations);
    }
}
