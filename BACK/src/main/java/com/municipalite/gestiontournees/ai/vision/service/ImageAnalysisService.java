package com.municipalite.gestiontournees.ai.vision.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.common.AiServiceException;
import com.municipalite.gestiontournees.ai.common.CollectionAiClient;
import com.municipalite.gestiontournees.ai.common.LocalVisionClient;
import com.municipalite.gestiontournees.ai.common.VisionImageInput;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.vision.model.AiProblem;
import com.municipalite.gestiontournees.ai.vision.model.ImageAnalysis;
import com.municipalite.gestiontournees.ai.vision.repository.AiProblemRepository;
import com.municipalite.gestiontournees.ai.vision.repository.ImageAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Slf4j
public class ImageAnalysisService {
    public static final String PROMPT_VERSION = "point-cleanliness-v1";
    public static final String LOCAL_PROMPT_VERSION = "local-cleanliness-v1";
    public static final Set<String> OBSERVATION_LABELS = Set.of("CLEANED", "NOT_CLEANED", "OBSTRUCTED", "CANNOT_DETERMINE");
    private final CollectionAiClient collectionAiClient;
    private final AiProperties aiProperties;
    private final ImageAnalysisRepository imageAnalysisRepository;
    private final AiProblemRepository aiProblemRepository;
    @Qualifier("aiObjectMapper")
    private final ObjectMapper objectMapper;
    private final LocalVisionClient localVisionClient;

    /** LLM-only construction (no local client), kept for existing callers and tests. */
    public ImageAnalysisService(CollectionAiClient collectionAiClient, AiProperties aiProperties,
                                ImageAnalysisRepository imageAnalysisRepository, AiProblemRepository aiProblemRepository,
                                ObjectMapper objectMapper) {
        this(collectionAiClient, aiProperties, imageAnalysisRepository, aiProblemRepository, objectMapper, null);
    }

    public record EvaluationConfiguration(String provider, String model, String promptVersion) {}

    public EvaluationConfiguration evaluationConfiguration() {
        if (aiProperties.getVisionEngine() == AiProperties.VisionEngine.LOCAL) {
            // A different provider/model/version means reusableVisitAnalysis never reuses another engine's result.
            AiProperties.LocalVision local = aiProperties.getLocalVision();
            return new EvaluationConfiguration("LOCAL", local.getModelName() + "@sha256:" + local.getModelSha256(), LOCAL_PROMPT_VERSION);
        }
        return new EvaluationConfiguration(aiProperties.getCollectionProvider().name(), aiProperties.getCollectionVisionModel(), PROMPT_VERSION);
    }

    /** Reuse only this visit's original assessment of the same immutable photo references. */
    public Optional<ImageAnalysis> reusableVisitAnalysis(String analysisId, String afterPhotoUrl,
            String beforePhotoUrl, String pointId, String tourId, String driverId) {
        if (analysisId == null || analysisId.isBlank() || tourId == null || pointId == null) return Optional.empty();
        EvaluationConfiguration configuration = evaluationConfiguration();
        return imageAnalysisRepository.findById(analysisId).filter(previous ->
                OBSERVATION_LABELS.contains(previous.getStatus() == null ? "" : previous.getStatus())
                && previous.getFailureCategory() == null
                && Objects.equals(previous.getTourneeId(), tourId)
                && Objects.equals(previous.getPointCollecteId(), pointId)
                && Objects.equals(previous.getChauffeurId(), driverId)
                && Objects.equals(previous.getAfterPhotoUrl(), afterPhotoUrl)
                && Objects.equals(previous.getBeforePhotoUrl(), beforePhotoUrl)
                && Objects.equals(previous.getProvider(), configuration.provider())
                && Objects.equals(previous.getModel(), configuration.model())
                && Objects.equals(previous.getPromptVersion(), configuration.promptVersion()));
    }

    /** Supervisor experiment: reuse the operational prompt without modifying a tour. */
    public ImageAnalysis evaluateEvidence(MultipartFile after, MultipartFile before,
                                          String expectedModel, String expectedPromptVersion) {
        if (!Objects.equals(expectedModel, evaluationConfiguration().model()) ||
                !Objects.equals(expectedPromptVersion, evaluationConfiguration().promptVersion())) {
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
        EvaluationConfiguration configuration = evaluationConfiguration();
        ImageAnalysis analysis = ImageAnalysis.builder()
                .photoUrl(afterPhotoUrl).afterPhotoUrl(afterPhotoUrl).beforePhotoUrl(paired ? beforePhotoUrl : null)
                .inputMode(paired ? "BEFORE_AFTER" : "AFTER_ONLY")
                .pointCollecteId(pointCollecteId).tourneeId(tourneeId).chauffeurId(chauffeurId)
                .provider(configuration.provider()).model(configuration.model()).promptVersion(configuration.promptVersion())
                .createdAt(LocalDateTime.now()).problemCreated(false).build();
        try {
            if (aiProperties.getVisionEngine() == AiProperties.VisionEngine.LOCAL) {
                // Only the stored AFTER photo is sent; inputMode still records whether a BEFORE photo exists.
                applyLocalPrediction(analysis, readStoredPhoto(afterPhotoUrl));
            } else {
                List<VisionImageInput> images = new ArrayList<>();
                if (paired) images.add(readStoredPhoto(beforePhotoUrl));
                images.add(readStoredPhoto(afterPhotoUrl));
                String prompt = buildVisionPrompt(paired);
                String response = collectionAiClient.analyzeImages(images, prompt);
                JsonNode result = objectMapper.readTree(response);
                if (result == null || !result.isObject()) {
                    throw new AiServiceException("Réponse d'analyse invalide.", "INVALID_ANALYSIS_RESPONSE", null, null);
                }
                String label = result.path("status").asText("");
                String description = result.path("description").asText("").trim();
                if (!result.path("status").isTextual() || !result.path("description").isTextual()
                        || !OBSERVATION_LABELS.contains(label) || description.isBlank() || description.length() > 2000) {
                    throw new AiServiceException("Réponse d'analyse invalide.", "INVALID_ANALYSIS_RESPONSE", null, null);
                }
                analysis.setStatus(label);
                analysis.setDescription(description);
            }
        } catch (AiServiceException e) {
            markUnavailable(analysis, e.getCategory(), e.getProviderHttpStatus());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            markUnavailable(analysis, "INVALID_ANALYSIS_RESPONSE", null);
        } catch (IOException e) {
            markUnavailable(analysis, "EVIDENCE_UNAVAILABLE", null);
        } catch (Exception e) {
            markUnavailable(analysis, "ANALYSIS_FAILED", null);
        }
        // A model output is evidence for review, not an automatically established incident.
        return imageAnalysisRepository.save(analysis);
    }

    private void applyLocalPrediction(ImageAnalysis analysis, VisionImageInput after) {
        if (localVisionClient == null) {
            throw new AiServiceException("Le moteur de vision local n'est pas configuré.", "NOT_CONFIGURED", null, null);
        }
        LocalVisionClient.Prediction prediction = localVisionClient.predict(after);
        LocalCleanlinessMapper.Result result = LocalCleanlinessMapper.map(prediction, aiProperties.getLocalVision().getPropreThreshold());
        analysis.setStatus(result.status());
        analysis.setDescription(result.description());
        // Review priority only: the decision never validates the visit nor blocks the end of the tour.
        analysis.setDecision(result.decision());
        analysis.setProbabilities(prediction.probabilities());
        analysis.setPredictedClass(prediction.predictedClass());
        analysis.setQualityIssue(prediction.qualityIssue());
        analysis.setSharpness(prediction.sharpness());
        analysis.setMeanBrightness(prediction.meanBrightness());
        analysis.setModelSha256(prediction.modelSha256());
    }

    private void markUnavailable(ImageAnalysis analysis, String category, Integer providerHttpStatus) {
        analysis.setStatus("ANALYSIS_UNAVAILABLE");
        analysis.setFailureCategory(category);
        analysis.setProviderHttpStatus(providerHttpStatus);
        String explanation = Integer.valueOf(429).equals(providerHttpStatus)
                ? "La limite temporaire du service IA est atteinte. Réessayez plus tard."
                : "Analyse indisponible. Vous pouvez réessayer.";
        analysis.setDescription(explanation + " Les photos restent enregistrées pour la vérification humaine.");
        log.warn("Vision unavailable: provider={} model={} category={} httpStatus={}",
                analysis.getProvider(), analysis.getModel(), category, providerHttpStatus);
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

    private VisionImageInput readStoredPhoto(String reference) throws IOException {
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

    private VisionImageInput validatedImage(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024 || ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
            throw new IOException("Photo JPEG ou PNG lisible requise (10 Mo maximum).");
        }
        boolean jpeg = bytes.length > 2 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216;
        boolean png = bytes.length > 8 && (bytes[0] & 255) == 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71;
        if (!jpeg && !png) throw new IOException("Photo JPEG ou PNG requise.");
        return new VisionImageInput(bytes, jpeg ? "image/jpeg" : "image/png");
    }

    private String savePhotoFile(MultipartFile file) {
        try {
            VisionImageInput input = validatedImage(file.getBytes());
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
