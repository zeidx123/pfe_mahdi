package com.municipalite.gestiontournees.ai.assistant.controller;

import com.municipalite.gestiontournees.ai.assistant.model.ChatConversation;
import com.municipalite.gestiontournees.ai.assistant.service.AssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST pour l'Assistant IA du Superviseur et Statistiques IA.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERVISEUR')")
public class AssistantController {

    private final AssistantService assistantService;

    /**
     * POST /api/ai/assistant/chat
     * Envoyer une question à l'assistant IA superviseur.
     */
    @PostMapping("/assistant/chat")
    public ResponseEntity<ChatConversation> chat(
            @RequestBody Map<String, String> payload,
            Authentication authentication) {
        String conversationId = payload.get("conversationId");
        String message = payload.get("message");
        String email = authentication.getName();

        ChatConversation conversation = assistantService.chat(conversationId, email, message);
        return ResponseEntity.ok(conversation);
    }

    /**
     * GET /api/ai/assistant/conversations
     * Obtenir l'historique des conversations de l'assistant.
     */
    @GetMapping("/assistant/conversations")
    public ResponseEntity<List<ChatConversation>> getConversations(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(assistantService.getSupervisorConversations(email));
    }

    /**
     * GET /api/ai/statistics/summary
     * Obtenir un résumé statistique global IA du système.
     */
    @GetMapping("/statistics/summary")
    public ResponseEntity<Map<String, Object>> getSummaryStats() {
        return ResponseEntity.ok(assistantService.getSummaryStats());
    }
}
