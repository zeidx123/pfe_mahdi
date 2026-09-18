package com.municipalite.gestiontournees.ai.assistant.service;

import com.municipalite.gestiontournees.ai.assistant.model.ChatConversation;
import com.municipalite.gestiontournees.ai.assistant.model.ChatMessage;
import com.municipalite.gestiontournees.ai.assistant.repository.ChatConversationRepository;
import com.municipalite.gestiontournees.ai.assistant.service.tools.AssistantTools;
import com.municipalite.gestiontournees.ai.common.GeminiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service orchestrateur de l'Assistant IA Superviseur (Messagerie Humaine Intelligente Site-Wide).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AssistantService {

    private final GeminiClient geminiClient;
    private final AssistantTools assistantTools;
    private final ChatConversationRepository conversationRepository;

    public ChatConversation chat(String conversationId, String supervisorEmail, String userMessage) {
        if (userMessage == null || userMessage.isBlank() || userMessage.length() > 2000) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Message de 1 à 2000 caractères requis.");
        ChatConversation conversation;

        if (conversationId != null && !conversationId.isEmpty()) {
            conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Conversation introuvable."));
            if (!java.util.Objects.equals(conversation.getSupervisorEmail(), supervisorEmail)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Conversation non accessible.");
        } else {
            conversation = createNewConversation(supervisorEmail, userMessage);
        }

        // Ajouter message utilisateur
        conversation.getMessages().add(ChatMessage.builder()
                .role("user")
                .content(userMessage)
                .timestamp(LocalDateTime.now())
                .build());

        // Préparer le prompt augmenté avec le contexte système RAG complet
        String prompt = buildPrompt(conversation, userMessage);

        String assistantResponse;
        String responseStatus = "ANSWERED";
        try {
            assistantResponse = geminiClient.generateText(prompt);
        } catch (Exception e) {
            responseStatus = "PROVIDER_UNAVAILABLE";
            assistantResponse = "Le fournisseur IA est temporairement indisponible. Consultez les écrans de gestion ou réessayez.";
        }

        // Ajouter réponse assistant
        conversation.getMessages().add(ChatMessage.builder()
                .role("assistant")
                .status(responseStatus)
                .content(assistantResponse)
                .timestamp(LocalDateTime.now())
                .build());

        conversation.setUpdatedAt(LocalDateTime.now());
        return conversationRepository.save(conversation);
    }

    private ChatConversation createNewConversation(String email, String firstMessage) {
        String title = firstMessage.length() > 35 ? firstMessage.substring(0, 35) + "..." : firstMessage;
        return ChatConversation.builder()
                .supervisorEmail(email)
                .title(title)
                .messages(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private String buildPrompt(ChatConversation conversation, String currentQuestion) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tu es l'Assistant IA co-pilote du superviseur de la plateforme municipale GestiCollecte.\n");
        sb.append("Le superviseur est un ÊTRE HUMAIN responsable de la propreté urbaine et des équipes sur le terrain.\n");
        sb.append("Adopte une posture chaleureuse, respectueuse, fluide, synthétique et très claire.\n");
        sb.append("Fais TRÈS ATTENTION à l'intention du superviseur :\n");
        sb.append("- S'il demande le NOMBRE / COMPTE de camions en panne ou d'agents, donne les chiffres réels et l'état exact.\n");
        sb.append("- Ne réponds PAS avec une procédure s'il a simplement demandé combien d'éléments sont en panne ou actifs.\n\n");

        sb.append(assistantTools.buildSystemContext());

        sb.append("\nCONSIGNES DE RÉPONSE :\n");
        sb.append("- Réponds précisément avec les données réelles (noms, immatriculations, chiffres, matricules) fournies ci-dessus.\n");
        sb.append("- Si une information spécifique n'est pas présente, indique que l'information est insuffisante. N'invente jamais de procédure ou consigne. Utilise l'assistant documentaire pour les procédures approuvées.\n\n");

        sb.append("HISTORIQUE RÉCENT :\n");
        int start = Math.max(0, conversation.getMessages().size() - 6);
        for (int i = start; i < conversation.getMessages().size() - 1; i++) {
            ChatMessage m = conversation.getMessages().get(i);
            sb.append(m.getRole().toUpperCase()).append(": ").append(m.getContent()).append("\n");
        }

        sb.append("\nMESSAGE DU SUPERVISEUR :\n").append(currentQuestion).append("\n\n");
        sb.append("RÉPONSE DÉDIÉE DE L'ASSISTANT IA :");
        return sb.toString();
    }

    public List<ChatConversation> getSupervisorConversations(String email) {
        return conversationRepository.findBySupervisorEmailOrderByUpdatedAtDesc(email);
    }

    public Map<String, Object> getSummaryStats() {
        return assistantTools.getGlobalStatistics();
    }
}
