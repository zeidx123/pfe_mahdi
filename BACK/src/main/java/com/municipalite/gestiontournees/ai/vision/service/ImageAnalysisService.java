package com.municipalite.gestiontournees.ai.vision.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.common.GeminiClient;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.vision.model.AiProblem;
import com.municipalite.gestiontournees.ai.vision.model.ImageAnalysis;
import com.municipalite.gestiontournees.ai.vision.repository.AiProblemRepository;
import com.municipalite.gestiontournees.ai.vision.repository.ImageAnalysisRepository;
import com.municipalite.gestiontournees.pointcollecte.model.PointCollecte;
import com.municipalite.gestiontournees.pointcollecte.repository.PointCollecteRepository;
import com.municipalite.gestiontournees.tournee.model.Tournee;
import com.municipalite.gestiontournees.tournee.repository.TourneeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service pour l'Agent IA d'Analyse des Photos des Points de Collecte.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ImageAnalysisService {

    private final GeminiClient geminiClient;
    private final AiProperties aiProperties;
    private final ImageAnalysisRepository imageAnalysisRepository;
    private final AiProblemRepository aiProblemRepository;
    private final TourneeRepository tourneeRepository;
    private final PointCollecteRepository pointCollecteRepository;

    @Qualifier("aiObjectMapper")
    private final ObjectMapper objectMapper;

    /**
     * Analyser une photo prise par un chauffeur à un point de collecte.
     */
    public ImageAnalysis analyzeImage(MultipartFile file, String pointCollecteId, String tourneeId, String chauffeurId, Double latitude, Double longitude) {
        String savedFilename = savePhotoFile(file);
        String photoUrl = "uploads/photos/" + savedFilename;

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Impossible de lire l'image envoyée.");
        }

        String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
        String prompt = buildVisionPrompt();

        String geminiResponse;
        ImageAnalysis analysis;

        try {
            geminiResponse = geminiClient.analyzeImage(bytes, contentType, prompt);
            analysis = parseVisionResponse(geminiResponse);
        } catch (Exception e) {
            log.warn("Erreur lors de l'appel Gemini Vision : {}. Utilisation analyse de secours.", e.getMessage());
            analysis = ImageAnalysis.builder()
                    .status("NORMAL")
                    .typeAnomalie("CONTENEUR_NORMAL")
                    .confidence(0.70)
                    .description("Conteneur inspecté. Analyse incertaine — vérification humaine recommandée.")
                    .severity("INFO")
                    .build();
        }

        analysis.setPhotoUrl(photoUrl);
        analysis.setPointCollecteId(pointCollecteId);
        analysis.setTourneeId(tourneeId);
        analysis.setChauffeurId(chauffeurId);
        analysis.setCreatedAt(LocalDateTime.now());

        // Créer un problème automatique si anomalie détectée avec confiance suffisante
        double threshold = aiProperties.getVision().getConfidenceThresholdAnomaly();
        if ("ANOMALIE".equals(analysis.getStatus()) && analysis.getConfidence() != null && analysis.getConfidence() >= threshold) {
            AiProblem problem = createProblem(analysis, latitude, longitude);
            analysis.setProblemCreated(true);
            analysis.setProblemId(problem.getId());
        } else {
            analysis.setProblemCreated(false);
        }

        return imageAnalysisRepository.save(analysis);
    }

    private String buildVisionPrompt() {
        return """
            Tu es un expert en inspection visuelle des points de collecte de déchets municipaux.
            Examine attentivement l'image ci-jointe.
            
            Détecte l'une des catégories suivantes :
            - CONTENEUR_NORMAL : conteneur en bon état, propre et non débordant.
            - CONTENEUR_PLEIN : rempli à 100% mais sans débordement majeur.
            - CONTENEUR_DEBORDE : déchets accumulés au-dessus du bac.
            - CONTENEUR_RENVERSE : conteneur tombé au sol.
            - CONTENEUR_ENDOMMAGE : conteneur cassé, brûlé ou déformé.
            - DECHETS_AUTOUR : déchets volumineux ou sacs entassés à côté du conteneur.
            - POINT_INACCESSIBLE : véhicule mal garé ou voie bloquée empêchant la collecte.
            - OBSTACLE : travaux ou objet bloquant le camion.
            - AUTRE_ANOMALIE : tout autre problème.

            Réponds UNIQUEMENT avec un JSON valide sous le format exact suivant :
            {
              "status": "NORMAL" ou "ANOMALIE",
              "typeAnomalie": "UN_DES_TYPES_CI_DESSUS",
              "confidence": 0.95,
              "description": "Explication claire et concise en français.",
              "severity": "INFO" ou "WARNING" ou "CRITICAL"
            }
            """;
    }

    private ImageAnalysis parseVisionResponse(String jsonText) {
        try {
            String cleaned = jsonText.trim().replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            JsonNode node = objectMapper.readTree(cleaned);

            String status = node.path("status").asText("NORMAL");
            String type = node.path("typeAnomalie").asText("CONTENEUR_NORMAL");
            double confidence = node.path("confidence").asDouble(0.80);
            String desc = node.path("description").asText("Analyse terminée.");
            String severity = node.path("severity").asText("INFO");

            return ImageAnalysis.builder()
                    .status(status)
                    .typeAnomalie(type)
                    .confidence(confidence)
                    .description(desc)
                    .severity(severity)
                    .build();
        } catch (Exception e) {
            log.error("Erreur parsing réponse Vision : {}", e.getMessage());
            return ImageAnalysis.builder()
                    .status("NORMAL")
                    .typeAnomalie("CONTENEUR_NORMAL")
                    .confidence(0.60)
                    .description("Analyse incertaine — vérification humaine recommandée.")
                    .severity("INFO")
                    .build();
        }
    }

    private AiProblem createProblem(ImageAnalysis analysis, Double lat, Double lon) {
        String camionId = null;
        if (analysis.getTourneeId() != null) {
            Tournee tournee = tourneeRepository.findById(analysis.getTourneeId()).orElse(null);
            if (tournee != null) {
                camionId = tournee.getCamionId();
            }
        }

        AiProblem problem = AiProblem.builder()
                .imageAnalysisId(analysis.getId())
                .tourneeId(analysis.getTourneeId())
                .pointCollecteId(analysis.getPointCollecteId())
                .chauffeurId(analysis.getChauffeurId())
                .camionId(camionId)
                .photoUrl(analysis.getPhotoUrl())
                .latitude(lat)
                .longitude(lon)
                .typeProbleme(analysis.getTypeAnomalie())
                .description(analysis.getDescription())
                .scoreConfiance(analysis.getConfidence())
                .severite(analysis.getSeverity())
                .statut("SIGNALEE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return aiProblemRepository.save(problem);
    }

    public ImageAnalysis confirmOrCorrect(String analysisId, Boolean validee, String correctionChauffeur) {
        ImageAnalysis analysis = imageAnalysisRepository.findById(analysisId)
                .orElseThrow(() -> new RuntimeException("Analyse introuvable : " + analysisId));

        analysis.setValideeParChauffeur(validee);
        analysis.setCorrectionChauffeur(correctionChauffeur);

        if (Boolean.FALSE.equals(validee) && correctionChauffeur != null) {
            analysis.setTypeAnomalie(correctionChauffeur);
            if ("CONTENEUR_NORMAL".equals(correctionChauffeur)) {
                analysis.setStatus("NORMAL");
                analysis.setSeverity("INFO");
            } else {
                analysis.setStatus("ANOMALIE");
            }
        }

        return imageAnalysisRepository.save(analysis);
    }

    private String savePhotoFile(MultipartFile file) {
        try {
            Path uploadDir = Paths.get(aiProperties.getStorage().getPhotosDir()).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);
            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "photo.jpg";
            String filename = System.currentTimeMillis() + "_" + originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path filePath = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return filename;
        } catch (IOException e) {
            log.error("Erreur sauvegarde photo : {}", e.getMessage(), e);
            throw new RuntimeException("Impossible de sauvegarder le fichier photo : " + e.getMessage());
        }
    }

    public List<ImageAnalysis> getAllAnalyses() {
        return imageAnalysisRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<AiProblem> getAllProblems() {
        return aiProblemRepository.findAllByOrderByCreatedAtDesc();
    }
}
