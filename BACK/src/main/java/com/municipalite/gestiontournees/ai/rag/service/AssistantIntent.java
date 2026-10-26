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
        String raw = message.trim().toLowerCase(Locale.ROOT);
        return text.matches("(?:bonjour|bonsoir|salut|coucou|hello|hi|hey|ahla|aslema|3aslema|marhba)(?: a toi| assistant| tout le monde| bik)?(?: ca va| comment ca va| how are you| labes| chnowa el jaw)?")
                || text.matches("(?:merci|merci beaucoup|merci bien|thanks|thank you|super merci|ok merci|d accord merci|au revoir|a bientot|bye|3aychek|aychek|chokran|sahha)(?: beaucoup)?")
                || Set.of("ca va", "comment ca va", "comment vas tu", "how are you", "qui es tu", "tu es qui",
                    "who are you", "que peux tu faire", "qu est ce que tu peux faire", "comment peux tu m aider",
                    "tu peux m aider", "peux tu m aider", "aide moi", "help", "what can you do", "can you help me",
                    "labes", "cv", "chneya ahwelek", "chnowa tnejjem taamel", "chkoun enti", "3aweni", "najjem naslek",
                    "صباح الخير", "مساء الخير", "أهلا", "اهلا", "عسلامة", "مرحبا", "يعيشك", "شكرا", "شكون انت", "شكون إنت",
                    "شنوة تنجم تعمل", "لاباس", "شحالك", "عاوني").contains(text)
                || Set.of("أهلا", "عسلامة", "مرحبا", "صباح الخير", "مساء الخير", "يعيشك", "شكرا", "لاباس").contains(raw);
    }

    static boolean draft(String message) {
        String text = normalize(message);
        // A request to write official instructions is a documentary question, even when phrased as a draft.
        if (text.matches(".*\\b(procedure|procedures|consigne|consignes|reglement|reglementation|obligatoire|obligatoires|officiel|officielle|mandatory|official|policy|instructions|قانون|إجراء|اجراءات|رسمي)\\b.*")) return false;
        return text.matches("^(?:(?:peux tu|pourrais tu|tu peux|can you|could you|please|aide moi a|help me|3aweni|najjem|tnejjem) )?(?:me )?(?:redige|rediger|ecris|ecrire|reformule|reformuler|corrige|corriger|write|draft|rewrite|rephrase|aamel|iktib)\\b.*\\b(message|note|reclamation|signalement|compte rendu|rapport|mail|email|report|complaint|risela|rsela)\\b.*")
                || text.matches("^(?:reformule|corrige|rewrite|rephrase)(?: ce texte| ceci| this| this text)? .+") && message.contains(":")
                || text.matches(".*(عاوني نكتب|اكتبلي مساج|اكتب مساج|صيغلي رسالة).*");
    }

    static boolean draftDetails(String message) {
        String text = normalize(message);
        return !message.contains("?") && !message.contains("؟")
                && !text.matches(".*\\b(procedure|consigne|reglement|obligatoire|officiel|officielle|mandatory|official|policy|instructions)\\b.*")
                && (text.matches("^(?:le point|la poubelle|le conteneur|la benne|mon point|point|p[0-9]+|the point|the bin|the container|el point|el bac|el benne|el poubelle)\\b.*")
                    || text.matches("^(?:النقطة|الحاوية|الكميون|البوان|الباك)\\b.*"));
    }

    static boolean styleFollowUp(String message) {
        String text = normalize(message);
        return Set.of("plus court", "plus simple", "plus formel", "plus naturel", "plus poli", "resume", "resume simplement",
                "peux tu resumer", "explique simplement", "explique plus simplement", "shorter", "simpler",
                "more formal", "explain more simply",
                "a9sar", "ashel", "fasserli akther", "fasserli ashel", "lakhass", "lakhasshali",
                "أقصر", "أسهل", "فسرلي بالباهي", "فسرلي أسهل", "لخصلي").contains(text);
    }
}
