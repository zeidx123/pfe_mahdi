package com.municipalite.gestiontournees.ai.rag.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Conservative routing: adding a procedural question to a greeting still requires documents. */
final class AssistantIntent {
    private AssistantIntent() {}

    static String normalize(String message) {
        return Normalizer.normalize(message, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    static boolean social(String message) {
        String text = normalize(message);
        return text.matches("(?:bonjour|bonsoir|salut|coucou|hello|hi|hey)(?: a toi| assistant| tout le monde)?(?: ca va| comment ca va| how are you)?")
                || text.matches("(?:merci|merci beaucoup|merci bien|thanks|thank you|super merci|ok merci|d accord merci|au revoir|a bientot|bye)(?: beaucoup)?")
                || Set.of("ca va", "comment ca va", "comment vas tu", "how are you", "qui es tu", "tu es qui",
                    "who are you", "que peux tu faire", "qu est ce que tu peux faire", "comment peux tu m aider",
                    "tu peux m aider", "peux tu m aider", "aide moi", "help", "what can you do", "can you help me").contains(text);
    }

    static boolean draft(String message) {
        String text = normalize(message);
        // A request to write official instructions is a documentary question, even when phrased as a draft.
        if (text.matches(".*\\b(procedure|procedures|consigne|consignes|reglement|reglementation|obligatoire|obligatoires|officiel|officielle|mandatory|official|policy|instructions)\\b.*")) return false;
        return text.matches("^(?:(?:peux tu|pourrais tu|tu peux|can you|could you|please|aide moi a|help me) )?(?:me )?(?:redige|rediger|ecris|ecrire|reformule|reformuler|corrige|corriger|write|draft|rewrite|rephrase)\\b.*\\b(message|note|reclamation|signalement|compte rendu|rapport|mail|email|report|complaint)\\b.*")
                || text.matches("^(?:reformule|corrige|rewrite|rephrase)(?: ce texte| ceci| this| this text)? .+") && message.contains(":");
    }

    static boolean draftDetails(String message) {
        String text = normalize(message);
        return !message.contains("?") && !message.contains("؟")
                && !text.matches(".*\\b(procedure|consigne|reglement|obligatoire|officiel|officielle|mandatory|official|policy|instructions)\\b.*")
                && text.matches("^(?:le point|la poubelle|le conteneur|la benne|mon point|point|p[0-9]+|the point|the bin|the container)\\b.*");
    }

    static boolean styleFollowUp(String message) {
        return Set.of("plus court", "plus simple", "plus formel", "plus naturel", "plus poli", "resume", "resume simplement",
                "peux tu resumer", "explique simplement", "explique plus simplement", "shorter", "simpler",
                "more formal", "explain more simply").contains(normalize(message));
    }
}
