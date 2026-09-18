package com.municipalite.gestiontournees.ai.vision.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.common.AiServiceException;
import com.municipalite.gestiontournees.ai.common.GeminiClient;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.vision.model.AiProblem;
import com.municipalite.gestiontournees.ai.vision.model.ImageAnalysis;
import com.municipalite.gestiontournees.ai.vision.repository.AiProblemRepository;
import com.municipalite.gestiontournees.ai.vision.repository.ImageAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageAnalysisService {
    public static final String PROMPT_VERSION = "point-cleanliness-v1";
    public static final Set<String> OBSERVATION_LABELS = Set.of("CLEANED", "NOT_CLEANED", "OBSTRUCTED", "CANNOT_DETERMINE");
    private final GeminiClient geminiClient;
    private final AiProperties aiProperties;
    private final ImageAnalysisRepository imageAnalysisRepository;
    private final AiProblemRepository aiProblemRepository;
    @Qualifier("aiObjectMapper")
    private final ObjectMapper objectMapper;

    public record EvaluationConfiguration(String provider, String model, String promptVersion) {}

    public EvaluationConfiguration evaluationConfiguration() {
        return new EvaluationConfiguration("GEMINI", aiProperties.getGemini().getModelVision(), PROMPT_VERSION);
    }

    /** Supervisor experiment: reuse the operational prompt without modifying a tour. */
    public ImageAnalysis evaluateEvidence(MultipartFile after, MultipartFile before,
                                          String expectedModel, String expectedPromptVersion) {
        if (!Objects.equals(expectedModel, aiProperties.getGemini().getModelVision()) ||
                !Objects.equals(expectedPromptVersion, PROMPT_VERSION)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La configuration d'évaluation a changé.");
        }
        if (after == null || after.isEmpty() || (before != null && before.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo APRÈS requise ; une photo AVANT fournie doit être lisible.");
        }
        return analyzeStoredEvidence(savePhotoFile(after), before == null ? null : savePhotoFile(before), null, null, null);
    }

    /** Compatibility endpoint. The controller checks tour ownership before storing a photo. */
    public ImageAnalysis analyzeImage(MultipartFile file, String pointCollecteId, String tourneeId,
                                      String chauffeurId, Double latitude, Double longitude) {
        return analyzeStoredEvidence(savePhotoFile(file), null, pointCollecteId, tourneeId, chauffeurId);
    }

    /** Called after the tour service has authorized the visit and saved its evidence. */
    public ImageAnalysis analyzeStoredEvidence(String afterPhotoUrl, String beforePhotoUrl,
                                               String pointCollecteId, String tourneeId, String chauffeurId) {
        if (afterPhotoUrl == null || afterPhotoUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Une photo APRÈS est requise pour l'analyse.");
        }
        boolean paired = beforePhotoUrl != null && !beforePhotoUrl.isBlank();
        ImageAnalysis analysis = ImageAnalysis.builder()
                .photoUrl(afterPhotoUrl).afterPhotoUrl(afterPhotoUrl).beforePhotoUrl(paired ? beforePhotoUrl : null)
                .inputMode(paired ? "BEFORE_AFTER" : "AFTER_ONLY")
                .pointCollecteId(pointCollecteId).tourneeId(tourneeId).chauffeurId(chauffeurId)
                .provider("GEMINI").model(aiProperties.getGemini().getModelVision()).promptVersion(PROMPT_VERSION)
                .createdAt(LocalDateTime.now()).problemCreated(false).build();
        try {
            List<GeminiClient.ImageInput> images = new ArrayList<>();
            if (paired) images.add(readStoredPhoto(beforePhotoUrl));
            images.add(readStoredPhoto(afterPhotoUrl));
            JsonNode result = objectMapper.readTree(geminiClient.analyzeImages(images, buildVisionPrompt(paired)));
            String label = result.path("status").asText("");
            String description = result.path("description").asText("").trim();
            if (!OBSERVATION_LABELS.contains(label) || description.isBlank() || description.length() > 2000) {
                throw new AiServiceException("Réponse d'analyse invalide.");
            }
            analysis.setStatus(label);
            analysis.setDescription(description);
        } catch (Exception e) {
            analysis.setStatus("ANALYSIS_UNAVAILABLE");
            analysis.setDescription("Analyse indisponible. Les photos restent enregistrées pour la vérification humaine. Vous pouvez réessayer.");
        }
        // A model output is evidence for review, not an automatically established incident.
        return imageAnalysisRepository.save(analysis);
    }

    private String buildVisionPrompt(boolean paired) {
        return """
                Tu aides à examiner des preuves visuelles d'un point de collecte municipal.
                %s
                Évalue uniquement ce qui est visible sur la photo APRÈS. Une paire peut aider à comparer le même point.
                N'affirme jamais qui a réalisé la collecte ni qu'une tâche est prouvée par la photo.
                Choisis exactement un état :
                CLEANED : le point paraît propre, sans déchets visibles restant à collecter.
                NOT_CLEANED : des déchets visibles paraissent rester au point.
                OBSTRUCTED : un obstacle visible empêche l'accès/la collecte.
                CANNOT_DETERMINE : cadrage, qualité, visibilité ou incohérence des preuves ne permettent pas de conclure.
                Si les images ne représentent pas clairement le même point, ou ne montrent pas un point de collecte,
                utilise CANNOT_DETERMINE. N'invente pas de pourcentage de confiance.
                Retourne uniquement un objet JSON : {"status":"CLEANED|NOT_CLEANED|OBSTRUCTED|CANNOT_DETERMINE",
                "description":"Explication brève en français fondée sur les éléments visibles."}
                """.formatted(paired ? "La première image est AVANT ; la seconde est APRÈS." : "Une seule image est fournie : APRÈS la collecte déclarée.");
    }

    public ImageAnalysis getAnalysis(String id) {
        return imageAnalysisRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analyse introuvable."));
    }

    public ImageAnalysis confirmOrCorrect(String analysisId, Boolean validee, String correctionChauffeur) {
        if (validee == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirmation requise.");
        if (!Boolean.TRUE.equals(validee) && !OBSERVATION_LABELS.contains(correctionChauffeur == null ? "" : correctionChauffeur)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez une observation valide.");
        }
        ImageAnalysis analysis = getAnalysis(analysisId);
        analysis.setValideeParChauffeur(validee);
        analysis.setCorrectionChauffeur(Boolean.TRUE.equals(validee) ? null : correctionChauffeur);
        return imageAnalysisRepository.save(analysis);
    }

    public ImageAnalysis review(String analysisId, String label, String notes, String reviewerId) {
        if (!OBSERVATION_LABELS.contains(label == null ? "" : label)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez une observation valide.");
        }
        ImageAnalysis analysis = getAnalysis(analysisId);
        analysis.setReviewedLabel(label);
        analysis.setReviewNotes(notes);
        analysis.setReviewedBy(reviewerId);
        analysis.setReviewedAt(LocalDateTime.now());
        return imageAnalysisRepository.save(analysis);
    }

    private GeminiClient.ImageInput readStoredPhoto(String reference) throws IOException {
        String clean = reference.startsWith("/") ? reference.substring(1) : reference;
        if (!clean.startsWith("uploads/photos/")) throw new IOException("Référence photo invalide.");
        String filename = clean.substring("uploads/photos/".length());
        if (filename.isBlank() || !filename.matches("[A-Za-z0-9._-]+") || filename.equals(".") || filename.equals("..")) {
            throw new IOException("Référence photo invalide.");
        }
        Path root = Paths.get(aiProperties.getStorage().getPhotosDir()).toAbsolutePath().normalize();
        Path path = root.resolve(filename).normalize();
        if (!path.startsWith(root) || !path.toRealPath().startsWith(root.toRealPath()) || Files.size(path) > 10 * 1024 * 1024) {
            throw new IOException("Fichier photo invalide.");
        }
        byte[] bytes = Files.readAllBytes(path);
        return validatedImage(bytes);
    }

    private GeminiClient.ImageInput validatedImage(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024 || ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
            throw new IOException("Photo JPEG ou PNG lisible requise (10 Mo maximum).");
        }
        boolean jpeg = bytes.length > 2 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216;
        boolean png = bytes.length > 8 && (bytes[0] & 255) == 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71;
        if (!jpeg && !png) throw new IOException("Photo JPEG ou PNG requise.");
        return new GeminiClient.ImageInput(bytes, jpeg ? "image/jpeg" : "image/png");
    }

    private String savePhotoFile(MultipartFile file) {
        try {
            GeminiClient.ImageInput input = validatedImage(file.getBytes());
            Path root = Paths.get(aiProperties.getStorage().getPhotosDir()).toAbsolutePath().normalize();
            Files.createDirectories(root);
            String filename = UUID.randomUUID() + (input.mimeType().equals("image/png") ? ".png" : ".jpg");
            Files.write(root.resolve(filename), input.bytes());
            return "/uploads/photos/" + filename;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de lire ou enregistrer la photo.");
        }
    }

    public List<ImageAnalysis> getAllAnalyses() { return imageAnalysisRepository.findAllByOrderByCreatedAtDesc(); }
    public List<AiProblem> getAllProblems() { return aiProblemRepository.findAllByOrderByCreatedAtDesc(); }
}
