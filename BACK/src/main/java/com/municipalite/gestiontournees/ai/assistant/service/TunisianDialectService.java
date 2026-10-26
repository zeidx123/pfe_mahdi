package com.municipalite.gestiontournees.ai.assistant.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service de détection de langue et d'élaboration des instructions système pour
 * le support natif de la Derja Tunisienne (en caractères arabes ou latins/Arabizi),
 * du Français et de l'Arabe standard.
 */
@Service
public class TunisianDialectService {

    public enum LanguageStyle {
        DERJA_LATIN,   // Derja en caractères latins / Arabizi (chiffres 3, 5, 7, 9...)
        DERJA_ARABIC,  // Derja en caractères arabes
        FRENCH,        // Français
        STANDARD_ARABIC, // Arabe standard
        MIXED          // Mélange Derja / Français
    }

    private static final Pattern ARABIC_SCRIPT_PATTERN = Pattern.compile("[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF]");

    // Mots et marqueurs spécifiques à la Derja tunisienne en alphabet latin / Arabizi
    private static final Set<String> DERJA_LATIN_MARKERS = Set.of(
            "chnowa", "chnwa", "chneya", "chnia", "kifech", "wa9tech", "waqtech", "chkoun", "fin", "win", "winek",
            "najm", "najjem", "ynajjem", "tnajjem", "ennajjem", "bech", "bch", "mte3i", "mta3i", "mte3", "mta3",
            "hedha", "hedhi", "haka", "hakeka", "brabi", "brabbi", "3aychek", "aychek", "saha", "sahha", "ya3tik",
            "chouf", "nchouf", "tchouf", "mchit", "nemchi", "temchi", "aamel", "naamel", "taamel", "yaamel",
            "khedma", "khidma", "khaddem", "fama", "famma", "mafamech", "mafamach", "famech",
            "ken", "kan", "ey", "iyeh", "behi", "bahi", "labes", "lebes",
            "dkhalt", "odkhol", "nodkhol", "todkhol", "hott", "hot", "nhot", "thot",
            "mouch", "mech", "mouh", "mch", "tawa", "tawwa", "fel", "mel", "lel",
            "chwaya", "chwayya", "barcha", "dima", "wala", "wallah", "zeda", "ghadi", "honi", "hona",
            "klem", "ahki", "nahki", "9olli", "9oli", "goli", "alech", "3lech"
    );

    // Mots typiques de la Derja tunisienne en caractères arabes
    private static final Set<String> DERJA_ARABIC_MARKERS = Set.of(
            "شنوة", "شنو", "شنيّة", "شكون", "كيفاش", "وقتاش", "وين", "علاش", "باش", "بش",
            "نجم", "تنجم", "ينجم", "نجمش", "متاعي", "متاع", "هذا", "هذي", "هكا", "هكاكا",
            "بربي", "عيشك", "يعيشك", "فما", "فماش", "مافماش", "كان", "باهي", "لاباس", "برشا",
            "توة", "توا", "شوية", "ديما", "شفت", "نمشي", "نعمل", "تعمل", "يعمل", "نحط", "حط",
            "ندخل", "ادخل", "غادي", "هوني", "مش", "مشني", "قلي", "قولي", "زيد", "هبط"
    );

    /**
     * Détecte le style de langue et d'écriture de la requête utilisateur.
     */
    public LanguageStyle detectLanguageStyle(String text) {
        if (text == null || text.isBlank()) {
            return LanguageStyle.FRENCH;
        }

        boolean hasArabicScript = ARABIC_SCRIPT_PATTERN.matcher(text).find();
        if (hasArabicScript) {
            return LanguageStyle.DERJA_ARABIC;
        }

        String normalized = normalize(text);
        String[] words = normalized.split("\\s+");

        int derjaCount = 0;
        for (String w : words) {
            if (w.length() < 2) continue;
            if (DERJA_LATIN_MARKERS.contains(w)
                    || w.matches(".*[3795].*")
                    || (w.startsWith("el") && w.length() > 4 && !w.equals("elle") && !w.equals("elles") && !w.equals("element") && !w.equals("elements"))) {
                derjaCount++;
            }
        }

        if (derjaCount >= 1) {
            return LanguageStyle.DERJA_LATIN;
        }

        return LanguageStyle.FRENCH;
    }

    /**
     * Détermine si le texte contient principalement de l'alphabet arabe (pour le RTL).
     */
    public static boolean isArabicScript(String text) {
        if (text == null || text.isBlank()) return false;
        int arabicChars = 0;
        int totalLetters = 0;
        for (char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                totalLetters++;
                if (c >= 0x0600 && c <= 0x06FF || c >= 0x0750 && c <= 0x077F || c >= 0x08A0 && c <= 0x08FF) {
                    arabicChars++;
                }
            }
        }
        return totalLetters > 0 && ((double) arabicChars / totalLetters) > 0.35;
    }

    /**
     * Message en Derja tunisienne en cas d'absence d'information dans les documents.
     */
    public String getNoRelevantContextMessage(String question) {
        LanguageStyle style = detectLanguageStyle(question);
        if (style == LanguageStyle.DERJA_ARABIC) {
            return "ما لقيتش المعلومة هذي في الوثائق المتوفرة. نجّم نعاونك كان فمّا وثيقة فيها الإجراء المطلوب.";
        } else if (style == LanguageStyle.DERJA_LATIN) {
            return "Ma l9itech el ma3louma hedhi fel documents eli mawjoudin. Najjem n3awnek ken tzid document fih el procédure el matlouba.";
        } else {
            return "Je n'ai pas trouvé d'information suffisante dans les documents disponibles pour répondre à cette question.";
        }
    }

    /**
     * Instructions système centralisées pour le support de la Derja Tunisienne,
     * respectant l'absence totale d'hallucinations et la fidélité documentaire.
     */
    public String buildDialectSystemPrompt() {
        return """
                RÈGLES LINGUISTIQUES ET SUPPORT DE LA DERJA TUNISIENNE :
                1. COMPRÉHENSION : Tu comprends parfaitement l'arabe tunisien (Derja Tounsia) sous toutes ses formes :
                   - En alphabet arabe (ex: "كيفاش نعمل باش نشوف tournée متاعي؟")
                   - En alphabet latin / Arabizi avec chiffres 3 (ع), 7 (ح), 9 (ق), 5 (خ) (ex: "Kifech najm nchouf el tournee mte3i?", "Chnowa naamel ken el bac mkasser?")
                   - En français, en anglais, ou en mélange bilingue Derja-Français très fréquent en Tunisie.
                
                2. STYLE DE RÉPONSE :
                   - Si l'utilisateur écrit en Derja latine (Arabizi) : réponds en Derja tunisienne naturelle en alphabet latin simple, fluide et chaleureux.
                   - Si l'utilisateur écrit en Derja en lettres arabes : réponds en Derja tunisienne en écriture arabe naturelle (évite l'arabe littéraire guindé non parlé).
                   - Si l'utilisateur écrit en Français : réponds en Français clair et professionnel.
                   - Préserve toujours les termes techniques opérationnels du métier tels qu'ils sont utilisés sur le terrain en Tunisie :
                     "tournée", "chauffeur", "superviseur", "bac", "point de collecte", "réclamation", "signalement", "itinéraire", "application".
                   - N'utilise JAMAIS d'expressions d'autres dialectes (égyptien, khaliji, etc.) ; utilise exclusivement la Derja tunisienne authentique.
                
                3. RESPECT STRICT DU CONTEXTE ET RAG (ZÉRO HALLUCINATION) :
                   - Pour toute procédure, consigne ou information municipale, appuie-toi EXCLUSIVEMENT sur les faits et extraits fournis.
                   - Ne traduis pas les règles techniques d'une manière qui déforme leur sens.
                   - Si les extraits ne permettent pas de répondre, retourne {"status":"INSUFFICIENT_INFORMATION","answer":"","citations":[]}.
                """;
    }

    public String getLanguageDirective(String question) {
        LanguageStyle style = detectLanguageStyle(question);
        return switch (style) {
            case DERJA_LATIN -> """
                    INSTRUCTION DE LANGUE OBLIGATOIRE :
                    L'utilisateur s'exprime en DERJA TUNISIENNE (Arabizi / alphabet latin tunisien avec chiffres 3, 5, 7, 9...).
                    Tu DOIS OBLIGATOIREMENT RÉPONDRE EN DERJA TUNISIENNE en alphabet latin (Arabizi) naturel, chaleureux et fluide.
                    INTERDICTION FORMELLE DE RÉPONDRE EN FRANÇAIS.""";
            case DERJA_ARABIC -> """
                    INSTRUCTION DE LANGUE OBLIGATOIRE :
                    L'utilisateur s'exprime en DERJA TUNISIENNE (alphabet arabe tunisien).
                    Tu DOIS OBLIGATOIREMENT RÉPONDRE EN DERJA TUNISIENNE en lettres arabes naturelles.
                    INTERDICTION FORMELLE DE RÉPONDRE EN FRANÇAIS.""";
            case FRENCH -> """
                    INSTRUCTION DE LANGUE :
                    L'utilisateur s'exprime en Français. Réponds en Français clair, précis et professionnel.""";
            default -> "Réponds dans la même langue et le même dialecte que la question.";
        };
    }

    public static String normalize(String message) {
        if (message == null) return "";
        return Normalizer.normalize(message, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }
}
