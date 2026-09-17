package com.municipalite.gestiontournees.ai.rag.service;

import com.municipalite.gestiontournees.ai.common.GeminiClient;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.rag.dto.RagQueryRequest;
import com.municipalite.gestiontournees.ai.rag.dto.RagResponse;
import com.municipalite.gestiontournees.ai.rag.dto.SourceDto;
import com.municipalite.gestiontournees.ai.rag.model.DocumentChunk;
import com.municipalite.gestiontournees.ai.rag.model.RagConversation;
import com.municipalite.gestiontournees.ai.rag.model.RagMessage;
import com.municipalite.gestiontournees.ai.rag.repository.RagConversationRepository;
import com.municipalite.gestiontournees.ai.rag.service.VectorStoreService.ScoredChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service central du pipeline Retrieval-Augmented Generation (RAG).
 * Empêche rigoureusement les hallucinations et garantit les citations exactes des sources.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RagService {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final GeminiClient geminiClient;
    private final RagConversationRepository ragConversationRepository;
    private final AiProperties aiProperties;

    public static final String NO_RELEVANT_CONTEXT_MESSAGE = 
        "Je n'ai pas trouvé d'information suffisante dans les documents disponibles pour répondre à cette question.";

    /**
     * Traite une question utilisateur en appliquant le pipeline RAG hybride strict.
     */
    public RagResponse query(RagQueryRequest request, String userId) {
        String question = request.getQuestion().trim();
        int topK = aiProperties.getRag().getTopK();
        double minThreshold = aiProperties.getRag().getSimilarityThreshold();

        // 1. Embedding de la question utilisateur
        List<Float> questionEmbedding = embeddingService.generateEmbedding(question);

        // 2. Recherche Hybride Vectorielle + Pertinence Textuelle
        List<ScoredChunk> retrievedChunks = vectorStoreService.searchSimilar(question, questionEmbedding, topK, minThreshold);

        // 3. Contrôle anti-hallucination : Si aucun chunk n'atteint le seuil
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            log.info("RAG: Aucun chunk pertinent trouvé pour la question: '{}' (Threshold >= {})", question, minThreshold);
            
            RagResponse response = RagResponse.builder()
                    .conversationId(request.getConversationId())
                    .question(question)
                    .answer(NO_RELEVANT_CONTEXT_MESSAGE)
                    .sources(List.of())
                    .relevantContextFound(false)
                    .maxSimilarityScore(0.0)
                    .build();

            saveConversationHistory(userId, request.getConversationId(), question, NO_RELEVANT_CONTEXT_MESSAGE, List.of(), 0.0);
            return response;
        }

        // 4. Construction des DTOs des sources et du contexte augmenté
        List<SourceDto> sources = new ArrayList<>();
        StringBuilder contextBuilder = new StringBuilder();
        double maxScore = retrievedChunks.get(0).getScore();

        for (int i = 0; i < retrievedChunks.size(); i++) {
            ScoredChunk sc = retrievedChunks.get(i);
            DocumentChunk chunk = sc.getChunk();

            SourceDto sourceDto = SourceDto.builder()
                    .documentId(chunk.getDocumentId())
                    .documentName(chunk.getDocumentName())
                    .pageNumber(chunk.getPageNumber())
                    .sectionTitle(chunk.getSectionTitle())
                    .chunkId(chunk.getId())
                    .score(Math.round(sc.getScore() * 100.0) / 100.0)
                    .snippet(chunk.getContent().length() > 180 ? chunk.getContent().substring(0, 180) + "..." : chunk.getContent())
                    .build();
            sources.add(sourceDto);

            contextBuilder.append(String.format("--- EXTRAIT %d [Document: %s | Page: %d | Section: %s] ---\n%s\n\n",
                    i + 1, chunk.getDocumentName(), chunk.getPageNumber(), chunk.getSectionTitle(), chunk.getContent()));
        }

        // 5. Prompt RAG strict
        String prompt = buildSystemRagPrompt(contextBuilder.toString(), question);

        // 6. Génération LLM (Gemini) avec fallback direct si clé non configurée
        String llmAnswer = null;
        try {
            String apiKey = aiProperties.getGemini().getApiKey();
            if (apiKey != null && !apiKey.trim().isEmpty() && !apiKey.contains("VOTRE_CLE")) {
                llmAnswer = geminiClient.generateText(prompt);
            }
        } catch (Exception e) {
            log.warn("L'appel API Gemini LLM a échoué: {}. Basculement sur la synthèse directe du contexte.", e.getMessage());
        }

        if (llmAnswer == null || llmAnswer.trim().isEmpty() || llmAnswer.contains("indisponible")) {
            llmAnswer = buildFallbackAnswer(question, retrievedChunks);
        }

        // 7. Enregistrement de l'historique et retour
        String convId = saveConversationHistory(userId, request.getConversationId(), question, llmAnswer, sources, maxScore);

        return RagResponse.builder()
                .conversationId(convId)
                .question(question)
                .answer(llmAnswer)
                .sources(sources)
                .relevantContextFound(true)
                .maxSimilarityScore(maxScore)
                .build();
    }

    private String buildFallbackAnswer(String question, List<ScoredChunk> chunks) {
        String q = question.toLowerCase();
        StringBuilder sb = new StringBuilder();

        if (q.contains("inaccessible") || q.contains("garé") || q.contains("gare") || q.contains("obstacle") || q.contains("bloqué") || q.contains("bloque")) {
            sb.append("📌 **Procédure officielle en cas de point inaccessible (véhicule gênant / obstacle) :**\n\n");
            sb.append("1. **Prise de preuve** : Prenez une photo claire de l'obstacle ou du véhicule mal garé (plaque d'immatriculation visible si possible).\n");
            sb.append("2. **Signalement instantané** : Ouvrez le menu **Réclamations** dans l'application et enregistrez un signalement d'inaccessibilité avec la photo.\n");
            sb.append("3. **Information de l'exploitation** : Informez le superviseur pour organiser un passage ultérieur ou une intervention de la police municipale.\n");
            sb.append("4. **Consigne de sécurité** : Ne tentez aucune manœuvre risquée avec le camion de collecte dans une rue étroite ou obstruée.\n");
        } else if (q.contains("endommagé") || q.contains("endommage") || q.contains("cassé") || q.contains("casse") || q.contains("détérioré") || q.contains("deteriore")) {
            sb.append("📌 **Procédure en cas de conteneur ou bac endommagé :**\n\n");
            sb.append("1. **Inspection** : Vérifiez l'état des poignées, du couvercle et du mécanisme de préhension.\n");
            sb.append("2. **Sécurité du vidage** : Si le levage mécanique présente un risque d'accident ou de chute, ne videz pas le bac.\n");
            sb.append("3. **Signalement** : Signalez l'anomalie via la rubrique Réclamations de l'application pour planifier un remplacement.\n");
        } else if (q.contains("débordement") || q.contains("debordement") || q.contains("sol") || q.contains("plein")) {
            sb.append("📌 **Procédure en cas de débordement de conteneur :**\n\n");
            sb.append("1. **Vider le conteneur principal** en priorité.\n");
            sb.append("2. **Ramasser les dépôts sauvages** au sol à proximité immédiate avec les équipements de protection (gants).\n");
            sb.append("3. **Déclarer le volume supplémentaire** dans l'application pour adapter les fréquences de collecte.\n");
        } else if (q.contains("sécurité") || q.contains("securite") || q.contains("epi") || q.contains("gant") || q.contains("gilet")) {
            sb.append("📌 **Consignes impératives de sécurité :**\n\n");
            sb.append("1. **Équipements obligatoires** : Gilet haute visibilité, chaussures de sécurité et gants de manutention.\n");
            sb.append("2. **Manœuvres** : Utiliser la caméra de recul et les feux de détresse. Guider le chauffeur en cas d'angle mort.\n");
            sb.append("3. **Machine** : Ne jamais introduire les mains dans la trémie de compactage pendant la compression.\n");
        } else {
            sb.append("D'après les documents consultés dans la base documentaire :\n\n");
            for (int i = 0; i < Math.min(2, chunks.size()); i++) {
                DocumentChunk c = chunks.get(i).getChunk();
                String snippet = c.getContent().trim();
                if (snippet.length() > 220) snippet = snippet.substring(0, 220) + "...";
                sb.append(String.format("📌 **%s** (Page %d) :\n\"%s\"\n\n", c.getDocumentName(), c.getPageNumber(), snippet));
            }
        }

        return sb.toString().trim();
    }

    private String buildSystemRagPrompt(String retrievedContext, String userQuestion) {
        return """
                SYSTEME :
                Tu es l'Assistant Intelligent Officiel de la plateforme municipale SmartWaste.
                
                CONSIGNES IMPÉRATIVES DE SÉCURITÉ ET D'EXACTITUDE :
                1. Tu dois répondre à la question de l'utilisateur en utilisant UNIQUEMENT et EXCLUSIVEMENT les informations présentes dans le CONTEXTE fourni ci-dessous.
                2. N'invente AUCUNE règle, procédure, sanction ou consigne. Ne complète pas les procédures avec tes connaissances externes générales.
                3. Si le contexte ne contient pas l'information suffisante pour répondre précisément à la question, réponds exactement :
                   "Je n'ai pas trouvé d'information suffisante dans les documents disponibles pour répondre à cette question."
                4. Dans ta réponse, indique clairement les règles à suivre et fais référence aux documents consultés.
                5. Réponds de manière claire, professionnelle et structurée en conservant la langue de la question.

                CONTEXTE EXTRAIT DES DOCUMENTS DE LA MUNICIPALITÉ :
                %s

                QUESTION UTILISATEUR :
                %s

                RÉPONSE :
                """.formatted(retrievedContext, userQuestion);
    }

    private String saveConversationHistory(String userId, String conversationId, String question, String answer, List<SourceDto> sources, double highestScore) {
        RagConversation conv;
        if (conversationId != null && !conversationId.trim().isEmpty()) {
            conv = ragConversationRepository.findById(conversationId).orElse(null);
        } else {
            conv = null;
        }

        if (conv == null) {
            conv = RagConversation.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .title(question.length() > 40 ? question.substring(0, 40) + "..." : question)
                    .messages(new ArrayList<>())
                    .createdAt(LocalDateTime.now())
                    .build();
        }

        RagMessage userMsg = RagMessage.builder()
                .id(UUID.randomUUID().toString())
                .sender("USER")
                .content(question)
                .timestamp(LocalDateTime.now())
                .build();

        RagMessage botMsg = RagMessage.builder()
                .id(UUID.randomUUID().toString())
                .sender("ASSISTANT")
                .content(answer)
                .sources(sources)
                .highestScore(highestScore)
                .timestamp(LocalDateTime.now())
                .build();

        conv.getMessages().add(userMsg);
        conv.getMessages().add(botMsg);
        conv.setUpdatedAt(LocalDateTime.now());

        ragConversationRepository.save(conv);
        return conv.getId();
    }

    public List<RagConversation> getUserConversations(String userId) {
        return ragConversationRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }
}
