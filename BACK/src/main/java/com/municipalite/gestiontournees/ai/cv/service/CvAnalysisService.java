package com.municipalite.gestiontournees.ai.cv.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.common.GeminiClient;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.cv.model.Candidate;
import com.municipalite.gestiontournees.ai.cv.repository.CandidateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service principal de l'Agent IA Analyse CV — Municipalité Tunisienne.
 * Règles : CIN tunisienne (8 chiffres), téléphone tunisien (+216 / 8 chiffres),
 *          âge 20-35 ans, permis B ou C minimum, expérience >= 2 ans.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CvAnalysisService {

    private final CvExtractionService cvExtractionService;
    private final GeminiClient geminiClient;
    private final AiProperties aiProperties;
    private final CandidateRepository candidateRepository;
    private final com.municipalite.gestiontournees.agent.service.AgentService agentService;
    private final com.municipalite.gestiontournees.user.repository.UserRepository userRepository;

    @Qualifier("aiObjectMapper")
    private final ObjectMapper objectMapper;

    // =====================================================================
    // REGEX TUNISIENNES
    // =====================================================================
    /** CIN tunisienne : 8 chiffres exactement */
    private static final Pattern CIN_REGEX = Pattern.compile("\\b([0-9]{8})\\b");

    /** Téléphone tunisien : 8 chiffres commençant par 2,3,4,5,7,9 ou avec préfixe +216/00216 */
    private static final Pattern PHONE_REGEX =
            Pattern.compile("(?:\\+216|00216)?\\s*([2-9][0-9]{7})");

    /** Email standard */
    private static final Pattern EMAIL_REGEX =
            Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");

    /** Numéro de permis (format libre, min 5 chars) */
    private static final Pattern PERMIS_NUM_REGEX =
            Pattern.compile("(?:num[eé]ro?\\s*(?:de\\s*)?permis|permis\\s*n[°o]?|n[°o]\\s*permis)\\s*[:\\-]?\\s*([A-Za-z0-9/_\\-]{4,20})",
                    Pattern.CASE_INSENSITIVE);

    /** Extraction années d'expérience — priorité aux formulations explicites */
    private static final Pattern EXP_YEARS_REGEX =
            Pattern.compile("(\\d{1,2})\\s*(?:ans?|ann[eé]es?)\\s*(?:d[''e]\\s*)?exp[eé]r",
                    Pattern.CASE_INSENSITIVE);

    /** Extraction dates "MM/YYYY - MM/YYYY" pour calcul d'expérience */
    private static final Pattern DATE_RANGE_REGEX =
            Pattern.compile("(\\d{2}/\\d{4})\\s*[-–]\\s*(\\d{2}/\\d{4}|(?:aujourd'?hui|present|actuel|en\\s*cours))",
                    Pattern.CASE_INSENSITIVE);

    /** Date de naissance JJ/MM/AAAA ou AAAA */
    private static final Pattern DOB_REGEX =
            Pattern.compile("(?:n[eé](?:\\s*le)?|date\\s*(?:de\\s*)?naissance)\\s*[:\\-]?\\s*(\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{4})",
                    Pattern.CASE_INSENSITIVE);

    /** Âge explicite dans le CV */
    private static final Pattern AGE_REGEX =
            Pattern.compile("(?:^|\\s|,|;)([2-9]\\d)\\s*ans(?:\\s|$|,|;|\\.)",
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    // =====================================================================
    // POINT D'ENTRÉE PRINCIPAL
    // =====================================================================

    public Candidate analyzeAndSaveCv(MultipartFile file) {
        // 1. Extraire le texte du PDF
        String cvText = cvExtractionService.extractTextFromPdf(file);

        // 2. Sauvegarder le fichier physiquement
        String savedFilename = saveFile(file);

        // 3. Tenter l'extraction via Gemini, sinon utiliser le parser local
        Candidate candidate;
        try {
            String prompt = buildExtractionPrompt(cvText);
            String geminiResponse = geminiClient.generateText(prompt);
            candidate = parseGeminiResponse(geminiResponse);
        } catch (Exception e) {
            log.warn("API Gemini non disponible ({}) → Utilisation du parseur heuristique local.", e.getMessage());
            candidate = parseCvLocally(cvText);
        }

        candidate.setCvFilePath("uploads/cv/" + savedFilename);
        candidate.setCvFileName(file.getOriginalFilename());
        candidate.setDateDepot(LocalDateTime.now());
        candidate.setDateMiseAJour(LocalDateTime.now());
        candidate.setStatut("EN_ATTENTE");

        // 4. Validation et nettoyage métier (règles tunisiennes)
        cleanAndValidateTunisian(candidate, cvText);

        // 5. CONTRÔLES ÉLIMINATOIRES : Si CIN incorrecte/vide, Permis absent, ou Âge hors [20, 35] ans → REJET AUTOMATIQUE
        boolean hasCin = isValidTunisianCin(candidate.getCin());
        boolean hasPermis = candidate.getNumeroPermis() != null && !candidate.getNumeroPermis().isBlank()
                && candidate.getTypePermis() != null && (candidate.getTypePermis().toUpperCase().contains("B") || candidate.getTypePermis().toUpperCase().contains("C"));

        Integer candidateAge = extractAgeFromCandidate(candidate);
        boolean isAgeValid = (candidateAge == null) || (candidateAge >= 20 && candidateAge <= 35);

        if (!hasCin || !hasPermis || !isAgeValid) {
            candidate.setScoreCompatibilite(0);
            candidate.setRecommandation("PEU_COMPATIBLE");
            candidate.setStatut("REJETE");
            String reason = buildRejectionReason(hasCin, hasPermis, candidateAge, isAgeValid);
            candidate.setDetailScore(reason);
            candidate.setAnalyseIa(reason + " Le CV ne peut pas être accepté.");
            log.info("🚫 CV {} rejeté automatiquement — CIN={} Permis={} Age={}", file.getOriginalFilename(), candidate.getCin(), candidate.getNumeroPermis(), candidateAge);
            return candidateRepository.save(candidate);
        }

        // 6. Calcul du score (système 100 pts avec les 3 grands axes)
        int score = calculateTunisianScore(candidate);
        candidate.setScoreCompatibilite(score);
        candidate.setRecommandation(determineRecommandation(score));
        candidate.setDetailScore(buildScoreDetail(candidate));

        // 7. Sauvegarde MongoDB
        return candidateRepository.save(candidate);
    }

    // =====================================================================
    // PROMPT GEMINI — RÈGLES TUNISIENNES
    // =====================================================================

    private String buildExtractionPrompt(String cvText) {
        return """
            Tu es un agent d'extraction de données de CV pour une municipalité tunisienne.
            Extrais les informations suivantes du CV. Réponds UNIQUEMENT avec un JSON valide.
            NE jamais inventer une information absente du CV. Si absent → null.
            
            RÈGLES IMPORTANTES pour la Tunisie :
            - CIN tunisienne = exactement 8 CHIFFRES (ex: 14628753, 09876543). PAS de lettres.
            - Téléphone tunisien = 8 chiffres commençant par 2,3,4,5,7 ou 9 (ex: 22456789, 98123456)
              ou avec préfixe +216 (ex: +21622456789)
            - Permis requis : B ou C minimum
            - Âge attendu : entre 20 et 35 ans
            
            CV à analyser :
            ---
            %s
            ---
            
            Réponds avec ce JSON exact (sans markdown, sans explication) :
            {
              "cin": "8 chiffres ou null",
              "nom": "string ou null",
              "prenom": "string ou null",
              "dateNaissance": "JJ/MM/AAAA ou null",
              "age": nombre entier ou null,
              "email": "string ou null",
              "telephone": "8 chiffres tunisiens ou null",
              "adresse": "string ou null",
              "numeroPermis": "string ou null",
              "typePermis": "ex: B, C ou B+C ou null",
              "dateObtentionPermis": "string ou null",
              "anneesExperience": nombre entier ou null,
              "experienceChauffeur": "résumé des expériences chauffeur ou null",
              "experienceCollecte": "expérience collecte déchets ou null",
              "formations": "liste formations ou null",
              "competences": "compétences clés ou null",
              "certifications": "certifications ou null",
              "disponibilite": "string ou null",
              "langues": "string ou null",
              "analyseIa": "résumé professionnel 2-3 phrases du profil du candidat"
            }
            """.formatted(cvText.length() > 8000 ? cvText.substring(0, 8000) : cvText);
    }

    // =====================================================================
    // PARSING RÉPONSE GEMINI
    // =====================================================================

    private Candidate parseGeminiResponse(String geminiResponse) {
        try {
            String cleaned = geminiResponse.trim()
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();

            JsonNode node = objectMapper.readTree(cleaned);
            Candidate c = new Candidate();

            c.setCin(getTextOrNull(node, "cin"));
            c.setNom(getTextOrNull(node, "nom"));
            c.setPrenom(getTextOrNull(node, "prenom"));
            c.setDateNaissance(getTextOrNull(node, "dateNaissance"));
            c.setEmail(getTextOrNull(node, "email"));
            c.setTelephone(getTextOrNull(node, "telephone"));
            c.setAdresse(getTextOrNull(node, "adresse"));
            c.setNumeroPermis(getTextOrNull(node, "numeroPermis"));
            c.setTypePermis(getTextOrNull(node, "typePermis"));
            c.setExperienceChauffeur(getTextOrNull(node, "experienceChauffeur"));
            c.setExperienceCollecte(getTextOrNull(node, "experienceCollecte"));
            c.setFormations(getTextOrNull(node, "formations"));
            c.setCompetences(getTextOrNull(node, "competences"));
            c.setCertifications(getTextOrNull(node, "certifications"));
            c.setDisponibilite(getTextOrNull(node, "disponibilite"));
            c.setLangues(getTextOrNull(node, "langues"));
            c.setAnalyseIa(getTextOrNull(node, "analyseIa"));

            // Expérience
            JsonNode expNode = node.path("anneesExperience");
            if (!expNode.isNull() && !expNode.isMissingNode()) {
                if (expNode.isNumber()) c.setAnneesExperience(expNode.asInt());
                else {
                    try { c.setAnneesExperience(Integer.parseInt(expNode.asText().replaceAll("[^0-9]", ""))); }
                    catch (NumberFormatException ignored) {}
                }
            }

            return c;
        } catch (Exception e) {
            log.error("Erreur parsing réponse Gemini CV : {}", e.getMessage());
            Candidate fallback = new Candidate();
            fallback.setAnalyseIa("L'analyse automatique n'a pas pu être complétée. Veuillez vérifier manuellement le CV.");
            return fallback;
        }
    }

    // =====================================================================
    // VALIDATION ET NETTOYAGE — RÈGLES TUNISIENNES
    // =====================================================================

    private void cleanAndValidateTunisian(Candidate c, String text) {
        String lower = text.toLowerCase();

        // --- 1. CIN Tunisienne (8 chiffres) ---
        // Chercher d'abord avec label CIN, puis seul
        if (!isValidTunisianCin(c.getCin())) {
            // Essai avec label "CIN :"
            Pattern cinLabeled = Pattern.compile(
                "(?:cin|carte\\s*(?:d['']?identit[eé]|nationale))\\s*[:\\-]?\\s*([0-9]{8})",
                Pattern.CASE_INSENSITIVE);
            Matcher m = cinLabeled.matcher(text);
            if (m.find()) {
                c.setCin(m.group(1));
            } else {
                // Chercher 8 chiffres consécutifs (heuristique)
                Matcher m2 = CIN_REGEX.matcher(text);
                // Éviter de prendre une année ou date comme CIN
                while (m2.find()) {
                    String candidate8 = m2.group(1);
                    // Exclure les années (1900-2099) et les dates partielles
                    int val = Integer.parseInt(candidate8);
                    if (val >= 10000000) { // CIN >= 10000000 pour éviter conflit années
                        c.setCin(candidate8);
                        break;
                    }
                    // Aussi accepter si précédé de "CIN" dans les 30 chars précédents
                    int start = Math.max(0, m2.start() - 30);
                    String context = text.substring(start, m2.start()).toLowerCase();
                    if (context.contains("cin") || context.contains("identit")) {
                        c.setCin(candidate8);
                        break;
                    }
                }
            }
        }

        // --- 2. Téléphone Tunisien ---
        if (!isValidTunisianPhone(c.getTelephone())) {
            Matcher m = PHONE_REGEX.matcher(text);
            if (m.find()) {
                c.setTelephone(m.group(1)); // 8 chiffres sans préfixe
            } else {
                c.setTelephone(null);
            }
        } else {
            // Normaliser : garder seulement les 8 chiffres
            String tel = c.getTelephone().replaceAll("[^0-9]", "");
            c.setTelephone(tel.length() >= 8 ? tel.substring(tel.length() - 8) : null);
        }

        // --- 3. Email ---
        if (c.getEmail() != null) {
            String email = c.getEmail().trim();
            if (!EMAIL_REGEX.matcher(email).matches()) {
                c.setEmail(null);
            } else {
                c.setEmail(email.toLowerCase());
            }
        }
        if (c.getEmail() == null) {
            Matcher em = EMAIL_REGEX.matcher(text);
            if (em.find()) c.setEmail(em.group().toLowerCase());
        }

        // --- 4. Nettoyage Nom / Prénom ---
        if (c.getNom() != null) {
            String nom = c.getNom().split("[\\r\\n]")[0]
                    .replaceAll("(?i)\\b(nom|prenom|prénom|curriculum|vitae|age|date|de|naissance|adresse|telephone|tel|email)\\b.*", "")
                    .trim();
            c.setNom(nom.isEmpty() ? null : nom);
        }
        if (c.getPrenom() != null) {
            String prenom = c.getPrenom().split("[\\r\\n]")[0]
                    .replaceAll("(?i)\\b(nom|prenom|prénom|curriculum|vitae|age|date|de|naissance|adresse|telephone|tel|email)\\b.*", "")
                    .trim();
            c.setPrenom(prenom.isEmpty() ? null : prenom);
        }

        // --- 5. Numéro de Permis ---
        if (c.getNumeroPermis() == null || c.getNumeroPermis().isBlank()
                || c.getNumeroPermis().equalsIgnoreCase("null")) {
            Matcher m = PERMIS_NUM_REGEX.matcher(text);
            if (m.find()) {
                c.setNumeroPermis(m.group(1).trim());
            } else {
                c.setNumeroPermis(null);
            }
        }

        // --- 6. Type de Permis (B ou C minimum pour Tunisie) ---
        StringBuilder permisFound = new StringBuilder();
        // Chercher catégories explicites
        if (text.matches("(?s).*\\bpermis\\s*[:\\-]?[^\\n]*\\bB\\b.*") 
            || lower.contains("categorie b") || lower.contains("catégorie b")) {
            permisFound.append("B, ");
        }
        if (lower.contains("permis c") || lower.contains("categorie c") || lower.contains("catégorie c")
            || lower.contains("poids lourds")) {
            permisFound.append("C, ");
        }
        if (lower.contains("permis ce") || lower.contains("super lourd")) {
            permisFound.append("CE, ");
        }
        if (lower.contains("permis d") || lower.contains("transport en commun")) {
            permisFound.append("D, ");
        }
        // Si "categories : B, C" dans le texte
        Matcher catMatcher = Pattern.compile(
            "(?:cat[eé]gorie[s]?|permis)\\s*[:\\-]?\\s*([A-Z,\\s+]{1,20})",
            Pattern.CASE_INSENSITIVE).matcher(text);
        if (catMatcher.find()) {
            String cats = catMatcher.group(1).toUpperCase().trim();
            // Extraire uniquement les lettres valides de permis
            if (cats.contains("B") && !permisFound.toString().contains("B")) permisFound.append("B, ");
            if (cats.contains("C") && !permisFound.toString().contains("C")) permisFound.append("C, ");
        }

        if (permisFound.length() > 0) {
            c.setTypePermis(permisFound.toString().replaceAll(",\\s*$", "").trim());
        } else if (c.getTypePermis() != null) {
            // Nettoyer la valeur Gemini : garder uniquement B, C, CE, D
            String cleaned = c.getTypePermis().toUpperCase()
                    .replaceAll("[^BCDE,+\\s]", "").trim();
            c.setTypePermis(cleaned.isEmpty() ? null : cleaned);
        }

        // --- 7. Expérience Professionnelle ---
        // PRIORITÉ 1 : formulation explicite "X ans d'expérience"
        Integer expFromText = extractExperienceYears(text);
        if (expFromText != null && expFromText > 0 && expFromText <= 45) {
            c.setAnneesExperience(expFromText);
        }
        // PRIORITÉ 2 : calcul depuis les plages de dates (MM/YYYY - MM/YYYY)
        if (c.getAnneesExperience() == null || c.getAnneesExperience() <= 0) {
            int totalMonths = calculateTotalExperienceMonths(text);
            if (totalMonths > 0) {
                c.setAnneesExperience(Math.max(1, totalMonths / 12));
            }
        }
        // Vérification cohérence : exclure valeur qui correspond à l'âge
        if (c.getAnneesExperience() != null) {
            int exp = c.getAnneesExperience();
            // L'âge extrait du CV
            Integer ageExtrait = extractAge(text);
            if (ageExtrait != null && exp == ageExtrait) {
                // Probablement confondu avec l'âge → recalculer depuis les dates
                int totalMonths = calculateTotalExperienceMonths(text);
                c.setAnneesExperience(totalMonths > 0 ? Math.max(1, totalMonths / 12) : null);
            }
            // Bornes cohérentes pour l'expérience
            if (c.getAnneesExperience() != null && (c.getAnneesExperience() <= 0 || c.getAnneesExperience() > 45)) {
                c.setAnneesExperience(null);
            }
        }

        // --- 8. Expériences Métier ---
        if (lower.contains("benne") || lower.contains("ordures") || lower.contains("bom")
                || (lower.contains("déchet") || lower.contains("dechet")) && lower.contains("collect")) {
            if (c.getExperienceCollecte() == null) {
                c.setExperienceCollecte("Conduite BOM et collecte de déchets municipaux.");
            }
        } else {
            c.setExperienceCollecte(null);
        }

        if (lower.contains("chauffeur") && (lower.contains("poids lourds") || lower.contains("camion")
                || lower.contains("bom") || lower.contains("benne") || lower.contains("porteur")
                || lower.contains("12 tonnes") || lower.contains("19 tonnes") || lower.contains("26 tonnes"))) {
            if (c.getExperienceChauffeur() == null) {
                c.setExperienceChauffeur("Expérience de chauffeur poids lourds.");
            }
        } else {
            c.setExperienceChauffeur(null);
        }
    }

    // =====================================================================
    // HELPERS EXTRACTION
    // =====================================================================

    /**
     * Extrait les années d'expérience depuis des formulations comme "4 ans d'expérience".
     * Évite de prendre l'âge du candidat.
     */
    private Integer extractExperienceYears(String text) {
        Matcher m = EXP_YEARS_REGEX.matcher(text);
        Integer best = null;
        while (m.find()) {
            try {
                int val = Integer.parseInt(m.group(1));
                if (val > 0 && val <= 45) {
                    if (best == null || val > best) best = val;
                }
            } catch (NumberFormatException ignored) {}
        }
        return best;
    }

    /**
     * Calcule le total de mois d'expérience depuis les plages de dates MM/YYYY - MM/YYYY.
     */
    private int calculateTotalExperienceMonths(String text) {
        Matcher m = DATE_RANGE_REGEX.matcher(text);
        int totalMonths = 0;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/yyyy");
        while (m.find()) {
            try {
                LocalDate start = LocalDate.parse("01/" + m.group(1), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                LocalDate end;
                String endStr = m.group(2).trim().toLowerCase();
                if (endStr.matches("(?:aujourd'?hui|present|actuel|en\\s*cours)")) {
                    end = LocalDate.now();
                } else {
                    end = LocalDate.parse("01/" + endStr, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                }
                if (end.isAfter(start)) {
                    totalMonths += Period.between(start, end).getMonths()
                                 + Period.between(start, end).getYears() * 12;
                }
            } catch (Exception ignored) {}
        }
        return totalMonths;
    }

    /**
     * Extrait l'âge explicitement mentionné dans le CV (ex: "28 ans").
     */
    private Integer extractAge(String text) {
        // Chercher "Age : 28" ou "28 ans" précédé d'une indication d'âge
        Pattern ageLabel = Pattern.compile(
            "(?:age|âge)\\s*[:\\-]?\\s*(\\d{2})",
            Pattern.CASE_INSENSITIVE);
        Matcher m = ageLabel.matcher(text);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        // Chercher "Date de naissance ... AAAA" et calculer l'âge
        Matcher dob = DOB_REGEX.matcher(text);
        if (dob.find()) {
            try {
                String dobStr = dob.group(1).replaceAll("[\\-.]", "/");
                LocalDate birthDate = LocalDate.parse(dobStr, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                return Period.between(birthDate, LocalDate.now()).getYears();
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** Vérifie si une CIN est valide au format tunisien (8 chiffres). */
    private boolean isValidTunisianCin(String cin) {
        return cin != null && cin.matches("^[0-9]{8}$");
    }

    /** Vérifie si un téléphone est valide au format tunisien. */
    private boolean isValidTunisianPhone(String phone) {
        if (phone == null) return false;
        String digits = phone.replaceAll("[^0-9]", "");
        // Accepter les 8 derniers chiffres
        if (digits.length() >= 8) {
            String last8 = digits.substring(digits.length() - 8);
            return last8.matches("^[2-9][0-9]{7}$");
        }
        return false;
    }

    // =====================================================================
    // PARSEUR LOCAL (FALLBACK SANS GEMINI)
    // =====================================================================

    private Candidate parseCvLocally(String text) {
        Candidate c = new Candidate();
        String lower = text.toLowerCase();

        // Email
        Matcher em = EMAIL_REGEX.matcher(text);
        if (em.find()) c.setEmail(em.group().toLowerCase());

        // Téléphone tunisien
        Matcher ph = PHONE_REGEX.matcher(text);
        if (ph.find()) c.setTelephone(ph.group(1));

        // Nom & Prénom — chercher les patterns "Nom : X" et "Prénom : Y"
        Matcher nomM = Pattern.compile("(?:nom)\\s*[:\\-]\\s*([\\p{L}\\s\\-]+)",
                Pattern.CASE_INSENSITIVE).matcher(text);
        if (nomM.find()) c.setNom(nomM.group(1).split("[\\r\\n,]")[0].trim().toUpperCase());

        Matcher prenomM = Pattern.compile("(?:pr[eé]nom)\\s*[:\\-]\\s*([\\p{L}\\s\\-]+)",
                Pattern.CASE_INSENSITIVE).matcher(text);
        if (prenomM.find()) c.setPrenom(prenomM.group(1).split("[\\r\\n,]")[0].trim());

        // Si nom/prénom non trouvés via labels, chercher la première ligne en majuscules
        if (c.getNom() == null) {
            String[] lines = text.split("\\n");
            for (String line : lines) {
                line = line.trim();
                if (line.matches("[A-ZÀÂÄÉÈÊËÏÎÔÙÛÜ][a-zàâäéèêëïîôùûü]+(\\s+[A-ZÀÂÄÉÈÊËÏÎÔÙÛÜ][a-zàâäéèêëïîôùûü]+)+")
                        && line.length() < 50) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 2) {
                        c.setNom(parts[parts.length - 1].toUpperCase());
                        c.setPrenom(parts[0]);
                    }
                    break;
                }
            }
        }

        // CIN Tunisienne (8 chiffres)
        Pattern cinLabeled = Pattern.compile(
            "(?:cin|carte\\s*(?:d['']?identit[eé]|nationale))\\s*[:\\-]?\\s*([0-9]{8})",
            Pattern.CASE_INSENSITIVE);
        Matcher cinM = cinLabeled.matcher(text);
        if (cinM.find()) {
            c.setCin(cinM.group(1));
        } else {
            // Chercher 8 chiffres standalone (hors dates)
            Matcher m2 = CIN_REGEX.matcher(text);
            while (m2.find()) {
                String val8 = m2.group(1);
                int intVal = Integer.parseInt(val8);
                // Exclure les années (19xx-20xx)
                if (intVal < 19000101 || intVal > 20991231) {
                    int start = Math.max(0, m2.start() - 30);
                    String ctx = text.substring(start, m2.start()).toLowerCase();
                    if (ctx.contains("cin") || ctx.contains("identit")) {
                        c.setCin(val8);
                        break;
                    }
                }
            }
        }

        // Numéro de Permis
        Matcher permisNumM = PERMIS_NUM_REGEX.matcher(text);
        if (permisNumM.find()) c.setNumeroPermis(permisNumM.group(1).trim());

        // Type de Permis
        StringBuilder permisB = new StringBuilder();
        if (lower.matches("(?s).*\\bcategorie[s]?\\s*[:\\-]?[^\\n]*\\bB\\b.*") 
            || lower.contains("permis b")) permisB.append("B, ");
        if (lower.contains("permis c") || lower.contains("poids lourds") 
            || lower.contains("categorie c")) permisB.append("C, ");
        if (lower.contains("permis ce") || lower.contains("super lourd")) permisB.append("CE, ");
        if (lower.contains("permis d")) permisB.append("D, ");

        // Pattern "categories : B, C" ou "categories : B, C, D"
        Matcher catM = Pattern.compile(
            "(?:cat[eé]gorie[s]?|permis)\\s*[:\\-]\\s*([B-D,\\s+]+)",
            Pattern.CASE_INSENSITIVE).matcher(text);
        if (catM.find()) {
            String cats = catM.group(1).toUpperCase();
            if (cats.contains("B") && !permisB.toString().contains("B")) permisB.append("B, ");
            if (cats.contains("C") && !permisB.toString().contains("C")) permisB.append("C, ");
        }

        if (permisB.length() > 0) {
            c.setTypePermis(permisB.toString().replaceAll(",\\s*$", "").trim());
        }

        // Expérience (en priorité via formulation explicite, puis calcul des dates)
        Integer expExplicite = extractExperienceYears(text);
        if (expExplicite != null) {
            c.setAnneesExperience(expExplicite);
        } else {
            int months = calculateTotalExperienceMonths(text);
            if (months > 0) c.setAnneesExperience(Math.max(1, months / 12));
        }
        // Anti-confusion âge/expérience
        Integer ageCandidat = extractAge(text);
        if (c.getAnneesExperience() != null && ageCandidat != null 
                && c.getAnneesExperience().equals(ageCandidat)) {
            int months = calculateTotalExperienceMonths(text);
            c.setAnneesExperience(months > 0 ? Math.max(1, months / 12) : null);
        }

        // Expériences métier
        if (lower.contains("benne") || lower.contains("ordures") || lower.contains("bom")
                || lower.contains("dechet") && lower.contains("collect")) {
            c.setExperienceCollecte("Conduite BOM et collecte de déchets municipaux.");
        }
        if (lower.contains("chauffeur") && (lower.contains("poids lourds") || lower.contains("camion")
                || lower.contains("bom") || lower.contains("benne"))) {
            c.setExperienceChauffeur("Expérience de chauffeur poids lourds.");
        }

        // Formations & certifications
        if (lower.contains("securite") || lower.contains("sécurité") || lower.contains("prevention")) {
            c.setCertifications("Formation Sécurité Routière et Prévention des Risques.");
        }
        if (lower.contains("immediat") || lower.contains("immédiat")) {
            c.setDisponibilite("Immédiate");
        }

        // Synthèse locale
        StringBuilder analyse = new StringBuilder("Analyse locale : ");
        analyse.append(c.getCin() != null ? "CIN " + c.getCin() + " trouvée. " : "CIN absente. ");
        analyse.append(c.getTypePermis() != null ? "Permis " + c.getTypePermis() + ". " : "Permis non détecté. ");
        analyse.append(c.getAnneesExperience() != null ? c.getAnneesExperience() + " an(s) d'expérience. " : "Expérience non détectable. ");
        if (c.getExperienceCollecte() != null) analyse.append("Expérience collecte déchets. ");
        if (c.getCertifications() != null) analyse.append("Formations sécurité présentes.");
        c.setAnalyseIa(analyse.toString().trim());

        return c;
    }

    // =====================================================================
    // SCORING TUNISIEN (100 PTS)
    // =====================================================================

    /**
     * Calcul du score de compatibilité selon les critères tunisiens.
     * Âge(15) + CIN(10) + Tél(5) + Permis B/C(20) + Exp≥2ans(25) + ExpChauffeur(15) + Complétude(10) = 100
     */
    private int calculateTunisianScore(Candidate c) {
        int score = 0;

        // 1. Âge conforme (20–35 ans) : 15 pts
        Integer age = extractAgeFromCandidate(c);
        if (age != null && age >= 20 && age <= 35) score += 15;

        // 2. CIN valide (8 chiffres) : 10 pts
        if (isValidTunisianCin(c.getCin())) score += 10;

        // 3. Téléphone valide : 5 pts
        if (isValidTunisianPhone(c.getTelephone())) score += 5;

        // 4. Permis B ou C présent : 20 pts
        if (c.getTypePermis() != null) {
            String p = c.getTypePermis().toUpperCase();
            if (p.contains("C") || p.contains("B")) score += 20;
        }

        // 5. Expérience ≥ 2 ans : 25 pts (partiel si 1 an)
        if (c.getAnneesExperience() != null) {
            if (c.getAnneesExperience() >= 5) score += 25;
            else if (c.getAnneesExperience() >= 2) score += 20;
            else if (c.getAnneesExperience() >= 1) score += 10;
        }

        // 6. Expérience chauffeur : 15 pts
        if (c.getExperienceChauffeur() != null && !c.getExperienceChauffeur().isBlank()) score += 15;

        // 7. Complétude du CV : 10 pts (≥ 5 champs présents)
        int fields = 0;
        if (c.getNom() != null) fields++;
        if (c.getPrenom() != null) fields++;
        if (c.getEmail() != null) fields++;
        if (c.getTelephone() != null) fields++;
        if (c.getAdresse() != null) fields++;
        if (c.getFormations() != null) fields++;
        if (c.getCertifications() != null) fields++;
        if (c.getCompetences() != null) fields++;
        if (fields >= 5) score += 10;
        else if (fields >= 3) score += 5;

        return Math.min(score, 100);
    }

    private Integer extractAgeFromCandidate(Candidate c) {
        // Calculer depuis dateNaissance si disponible
        if (c.getDateNaissance() != null && !c.getDateNaissance().isBlank()) {
            try {
                String dob = c.getDateNaissance().replaceAll("[\\-.]", "/");
                LocalDate birthDate = LocalDate.parse(dob, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                return Period.between(birthDate, LocalDate.now()).getYears();
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String buildScoreDetail(Candidate c) {
        StringBuilder sb = new StringBuilder();

        // Âge
        Integer age = extractAgeFromCandidate(c);
        if (age != null) {
            boolean ageOk = age >= 20 && age <= 35;
            sb.append(ageOk ? "✅" : "❌").append(" Âge ").append(age).append(" ans")
              .append(ageOk ? " (conforme 20-35)" : " (hors critère 20-35)").append(" : ")
              .append(ageOk ? "+15" : "+0").append(" pts\n");
        } else {
            sb.append("🟠 Âge non renseigné : +0 pts\n");
        }

        // CIN
        if (isValidTunisianCin(c.getCin())) {
            sb.append("✅ CIN ").append(c.getCin()).append(" (8 chiffres valide) : +10 pts\n");
        } else {
            sb.append("❌ CIN absente ou invalide (format 8 chiffres requis) : +0 pts\n");
        }

        // Téléphone
        if (isValidTunisianPhone(c.getTelephone())) {
            sb.append("✅ Téléphone ").append(c.getTelephone()).append(" (tunisien valide) : +5 pts\n");
        } else {
            sb.append("❌ Téléphone absent ou invalide : +0 pts\n");
        }

        // Permis
        if (c.getTypePermis() != null && (c.getTypePermis().contains("B") || c.getTypePermis().contains("C"))) {
            sb.append("✅ Permis ").append(c.getTypePermis()).append(" : +20 pts\n");
        } else {
            sb.append("❌ Permis B ou C absent : +0 pts\n");
        }

        // Expérience
        if (c.getAnneesExperience() != null) {
            int exp = c.getAnneesExperience();
            int pts = exp >= 5 ? 25 : exp >= 2 ? 20 : 10;
            sb.append(exp >= 2 ? "✅" : "⚠️").append(" ").append(exp).append(" an(s) d'expérience : +").append(pts).append(" pts\n");
        } else {
            sb.append("❌ Expérience non détectée : +0 pts\n");
        }

        // Expérience chauffeur
        if (c.getExperienceChauffeur() != null && !c.getExperienceChauffeur().isBlank()) {
            sb.append("✅ Expérience chauffeur poids lourds : +15 pts\n");
        } else {
            sb.append("❌ Expérience chauffeur absente : +0 pts\n");
        }

        // Complétude
        int fields = 0;
        if (c.getNom() != null) fields++;
        if (c.getPrenom() != null) fields++;
        if (c.getEmail() != null) fields++;
        if (c.getTelephone() != null) fields++;
        if (c.getAdresse() != null) fields++;
        if (c.getFormations() != null) fields++;
        if (c.getCertifications() != null) fields++;
        int compPts = fields >= 5 ? 10 : fields >= 3 ? 5 : 0;
        sb.append(compPts >= 10 ? "✅" : "⚠️").append(" Complétude CV (").append(fields).append(" champs) : +").append(compPts).append(" pts\n");

        return sb.toString().trim();
    }

    private String buildRejectionReason(boolean hasCin, boolean hasPermis, Integer age, boolean isAgeValid) {
        StringBuilder sb = new StringBuilder("❌ REJET AUTOMATIQUE : ");
        if (!hasCin) sb.append("Numéro CIN tunisien (8 chiffres) absent ou incorrect. ");
        if (!hasPermis) sb.append("Permis B ou C absent ou invalide. ");
        if (!isAgeValid) sb.append("L'âge (").append(age != null ? age + " ans" : "non renseigné").append(") est hors de la tranche obligatoire [20 - 35 ans]. ");
        return sb.toString().trim();
    }

    private String determineRecommandation(int score) {
        if (score >= aiProperties.getCv().getScoreThresholdRecommended()) return "RECOMMANDE";
        if (score >= aiProperties.getCv().getScoreThresholdExaminer()) return "A_EXAMINER";
        return "PEU_COMPATIBLE";
    }

    // =====================================================================
    // UTILITAIRES
    // =====================================================================

    private String saveFile(MultipartFile file) {
        try {
            Path uploadDir = Paths.get(aiProperties.getStorage().getCvDir()).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);
            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "cv.pdf";
            String filename = System.currentTimeMillis() + "_" + originalName.replaceAll("[^a-zA-Z0-9._\\-]", "_");
            Path filePath = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("CV sauvegardé : {}", filePath.toAbsolutePath());
            return filename;
        } catch (IOException e) {
            throw new RuntimeException("Impossible de sauvegarder le fichier CV : " + e.getMessage());
        }
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isNull() || n.isMissingNode()) return null;
        String val = n.asText("").trim();
        return val.isEmpty() || val.equalsIgnoreCase("null") ? null : val;
    }

    // =====================================================================
    // CRUD
    // =====================================================================

    public List<Candidate> getAllCandidates() {
        return candidateRepository.findAllByOrderByDateDepotDesc();
    }

    public Candidate getCandidateById(String id) {
        return candidateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Candidat introuvable avec l'id : " + id));
    }

    public Candidate updateDecision(String id, String statut, String notesSuperviseur) {
        Candidate candidate = getCandidateById(id);
        candidate.setStatut(statut);
        candidate.setNotesSuperviseur(notesSuperviseur);
        candidate.setDateMiseAJour(LocalDateTime.now());
        if ("ACCEPTE".equalsIgnoreCase(statut)) {
            createAgentFromCandidate(candidate);
        }
        return candidateRepository.save(candidate);
    }

    // =====================================================================
    // CRÉATION AGENT DEPUIS CANDIDAT ACCEPTÉ
    // =====================================================================

    private void createAgentFromCandidate(Candidate candidate) {
        String email = candidate.getEmail();
        if (email == null || email.isBlank() || !email.contains("@")) {
            email = "agent." + System.currentTimeMillis() + "@municipalite.tn";
            candidate.setEmail(email);
        }
        if (userRepository.findByEmail(email).isPresent()) {
            log.info("ℹ️ Agent déjà existant pour l'email : {}", email);
            return;
        }

        String cin = isValidTunisianCin(candidate.getCin()) ? candidate.getCin()
                : "CIN" + (10000000 + (int)(Math.random() * 89999999));

        String numPermis = (candidate.getNumeroPermis() != null && !candidate.getNumeroPermis().isBlank())
                ? candidate.getNumeroPermis() : cin;

        com.municipalite.gestiontournees.agent.dto.AgentRequest req = new com.municipalite.gestiontournees.agent.dto.AgentRequest();
        req.setNom(candidate.getNom() != null ? candidate.getNom() : "Nom");
        req.setPrenom(candidate.getPrenom() != null ? candidate.getPrenom() : "Agent");
        req.setEmail(email);
        req.setCin(cin);
        req.setTelephone(candidate.getTelephone() != null ? candidate.getTelephone() : "22000000");
        req.setAdresse(candidate.getAdresse() != null ? candidate.getAdresse() : "Adresse non renseignée");
        req.setNumeroPermis(numPermis);
        req.setTypePermis(candidate.getTypePermis() != null ? candidate.getTypePermis() : "B");
        req.setDateEmbauche(java.time.LocalDate.now());
        req.setDateExpirationPermis(java.time.LocalDate.now().plusYears(5));
        req.setActif(true);

        try {
            com.municipalite.gestiontournees.agent.dto.AgentResponse newAgent = agentService.createAgent(req);
            log.info("✅ Agent créé automatiquement : {} (Matricule: {})", newAgent.getEmail(), newAgent.getMatricule());
        } catch (Exception e) {
            log.error("Erreur création agent : {}", e.getMessage());
        }
    }
}
