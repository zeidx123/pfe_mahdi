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
        ChatConversation conversation;

        if (conversationId != null && !conversationId.isEmpty()) {
            conversation = conversationRepository.findById(conversationId)
                    .orElseGet(() -> createNewConversation(supervisorEmail, userMessage));
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
        try {
            assistantResponse = geminiClient.generateText(prompt);
        } catch (Exception e) {
            log.warn("Gemini API non disponible ({}) — Génération de réponse RAG locale basée sur l'intention exacte et les entités du site.", e.getMessage());
            assistantResponse = generateFallbackAnswer(userMessage);
        }

        // Ajouter réponse assistant
        conversation.getMessages().add(ChatMessage.builder()
                .role("assistant")
                .content(assistantResponse)
                .timestamp(LocalDateTime.now())
                .build());

        conversation.setUpdatedAt(LocalDateTime.now());
        return conversationRepository.save(conversation);
    }

    private ChatConversation createNewConversation(String email, String firstMessage) {
        String title = firstMessage.length() > 35 ? firstMessage.substring(0, 35) + "..." : firstMessage;
        return ChatConversation.builder()
                .supervisorEmail(email != null ? email : "admin@municipalite.ma")
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
        sb.append("- Si une information spécifique n'est pas présente, indique-le gentiment avec conseil.\n\n");

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

    private String generateFallbackAnswer(String question) {
        String q = question.trim().toLowerCase();
        Map<String, Object> stats = assistantTools.getGlobalStatistics();
        String context = assistantTools.buildSystemContext();

        // 1. Salutations et politesse humaine
        if (q.matches(".*(bonjour|salut|bonsoir|coucou|hello|qui es-tu|présente-toi).*")) {
            return "Bonjour Superviseur ! 👋\n\nJe suis votre assistant IA dédié. Je suis connecté en permanence à la plateforme GestiCollecte pour vous aider à gérer la flotte de camions, les chauffeurs, les tournées, la détection des problèmes par IA et le recrutement.\n\nComment puis-je vous aider aujourd'hui ?";
        }
        if (q.matches(".*(merci|super|parfait|excellent|d'accord|ok|c'est bon).*")) {
            return "C'est un plaisir de vous aider, Monsieur le Superviseur ! 😊\n\nN'hésitez pas si vous avez d'autres questions sur vos équipes ou vos véhicules sur le terrain. Passez une excellente journée !";
        }

        // 2. QUESTIONS SPÉCIFIQUES "COMBIEN DE CAMIONS EN PANNE" / ÉTAT DE PANNE DE LA FLOTTE
        if (q.contains("panne") && (q.contains("combien") || q.contains("nombre") || q.contains("est en") || q.contains("sont en") || q.contains("quels") || q.contains("savoir"))) {
            long pannes = ((Number) stats.getOrDefault("camionsEnPanne", 0)).longValue();
            long total = ((Number) stats.getOrDefault("totalCamions", 0)).longValue();
            long dispo = ((Number) stats.getOrDefault("camionsDisponibles", 0)).longValue();
            long tournee = ((Number) stats.getOrDefault("camionsEnTournee", 0)).longValue();
            long reclamationsPanne = ((Number) stats.getOrDefault("totalReclamationsPanne", 0)).longValue();

            if (pannes == 0 && reclamationsPanne == 0) {
                return String.format("🚛 État de la flotte de camions :\n\nActuellement, **aucun camion n'est en panne** ! 🎉\n\nSur les %s camion(s) de la flotte :\n• 🟢 %s camion(s) disponible(s)\n• 🚚 %s camion(s) actuellement en tournée\n• 🔴 0 camion en maintenance / panne\n\nTous vos véhicules sont 100%% opérationnels sur le terrain !", total, dispo, tournee);
            } else {
                return String.format("🚛 État des camions en panne / maintenance :\n\nActuellement, **%s camion(s)** sont signalés en panne ou en maintenance (sur un total de %s camions).\n• Réclamations de panne mécanique en attente : %s\n\nConsultez la rubrique \"Flotte de Camions\" pour voir les détails d'intervention.", pannes, total, reclamationsPanne);
            }
        }

        // 3. Procédures explicites (seulement si le terme procédure ou "que faire" est demandé)
        if (q.contains("procédure") || q.contains("procedure") || q.contains("consigne") || q.contains("que faire")) {
            if (q.contains("pneu") || q.contains("crevaison")) {
                return "📌 Consigne Sécurité - Pneu ou Crevaison :\n\n• Interdiction absolue de rouler sur un pneu dégonflé ou endommagé.\n• L'agent doit émettre une réclamation en direct.\n• Le service maintenance municipal intervient sur place pour remplacer la roue.";
            }
            return "📌 Procédure d'urgence en cas de panne de camion :\n\n1. L'agent doit stationner immédiatement le véhicule en sécurité sur le bas-côté.\n2. Allumer les feux de détresse et poser le triangle de sécurité à 30m.\n3. Signaler la panne via l'application mobile.\n4. En tant que superviseur, vous recevez l'alerte en direct sur le tableau de bord pour envoyer la dépanneuse municipale ou réaffecter un camion de remplacement.";
        }

        // 4. Questions "Combien" / Listes sur les autres entités
        if (q.contains("agent") || q.contains("chauffeur")) {
            if (q.contains("combien") || q.contains("nombre")) {
                return String.format("👤 Vos équipes d'agents :\n\nVotre système compte actuellement **%s chauffeur(s) / agent(s) municipal(aux)** enregistrés et actifs dans la base de données.", stats.get("totalAgents"));
            }
            return formatEntitySection(context, "DÉTAILS DES AGENTS / CHAUFFEURS MUNICIPAUX", "👤 Liste actuelle de vos chauffeurs et agents :");
        }

        if (q.contains("camion") || q.contains("flotte") || q.contains("véhicule")) {
            if (q.contains("combien") || q.contains("nombre")) {
                return String.format("🚚 Flotte municipale :\n\nVotre flotte compte **%s camion(s)** au total (%s disponibles, %s en tournée, %s en panne/maintenance).",
                        stats.get("totalCamions"), stats.get("camionsDisponibles"), stats.get("camionsEnTournee"), stats.get("camionsEnPanne"));
            }
            return formatEntitySection(context, "DÉTAILS DE LA FLOTTE DE CAMIONS", "🚚 État de la flotte municipale :");
        }

        if (q.contains("tournée") || q.contains("tournees")) {
            if (q.contains("combien") || q.contains("nombre")) {
                return String.format("🚛 Tournées de collecte :\n\nActuellement, **%s tournée(s)** sont enregistrées dans le système.", stats.get("totalTournees"));
            }
            return formatEntitySection(context, "DÉTAILS DES TOURNÉES", "🚛 Aperçu des tournées enregistrées :");
        }

        if (q.contains("cv") || q.contains("candidat") || q.contains("candidature") || q.contains("recrutement")) {
            if (q.contains("combien") || q.contains("nombre")) {
                return String.format("📄 Candidatures IA :\n\nVous avez reçu **%s candidature(s) CV** au total pour l'analyse IA.", stats.get("totalCandidatures"));
            }
            return formatEntitySection(context, "DÉTAILS DES CANDIDATURES CV", "📄 Candidatures CV enregistrées :");
        }

        if (q.contains("synthèse") || q.contains("statistique") || q.contains("global") || q.contains("résumé") || q.contains("bilan")) {
            return String.format("📊 Bilan global et instantané de GestiCollecte :\n\n• 👤 Chauffeurs / Agents actifs : %s\n• 🚚 Véhicules dans la flotte : %s\n• 🚛 Tournées créées : %s\n• 📍 Points de collecte répertoriés : %s\n• 🤖 Problèmes détectés par Vision IA : %s\n• 📩 Réclamations agents : %s\n• 📄 Candidatures CVs IA reçues : %s\n\nQue souhaitez-vous examiner en détail ?",
                    stats.get("totalAgents"), stats.get("totalCamions"), stats.get("totalTournees"),
                    stats.get("totalPointsCollecte"), stats.get("totalProblemesIa"),
                    stats.get("totalReclamations"), stats.get("totalCandidatures"));
        }

        return "Je suis à votre écoute, Monsieur le Superviseur. 😊\n\nVous pouvez me demander :\n• *\"Combien de camions sont en panne ?\"*\n• *\"Combien d'agents avons-nous ?\"*\n• *\"Donne-moi la liste de la flotte\"*\n• *\"Quelle est la procédure en cas d'accident ?\"*\n\nQue souhaitez-vous savoir ?";
    }

    private String formatEntitySection(String fullContext, String sectionKey, String headerTitle) {
        int idx = fullContext.indexOf(sectionKey);
        if (idx != -1) {
            int endIdx = fullContext.indexOf("\n\n", idx);
            if (endIdx == -1) endIdx = fullContext.length();
            String content = fullContext.substring(idx, endIdx).trim();
            return headerTitle + "\n\n" + content;
        }
        return headerTitle + "\n\nAucune donnée enregistrée pour le moment dans cette rubrique.";
    }

    public List<ChatConversation> getSupervisorConversations(String email) {
        return conversationRepository.findBySupervisorEmailOrderByUpdatedAtDesc(email != null ? email : "admin@municipalite.ma");
    }

    public Map<String, Object> getSummaryStats() {
        return assistantTools.getGlobalStatistics();
    }
}
