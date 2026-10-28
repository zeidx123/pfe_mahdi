package com.municipalite.gestiontournees.ai.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.common.AiServiceException;
import com.municipalite.gestiontournees.ai.common.CollectionAiClient;
import com.municipalite.gestiontournees.ai.common.LocalVisionClient;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.vision.model.ImageAnalysis;
import com.municipalite.gestiontournees.ai.vision.repository.*;
import com.municipalite.gestiontournees.ai.vision.service.ImageAnalysisService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Moteur LOCAL (service Python du modèle proprete_mobilenetv3l), le client HTTP étant simulé. */
class ImageAnalysisServiceLocalTest {
    private static final String SHA = "da40e684d2ddc268ae3c976e668dcd2a9861a5e42c46d6143a1dea27b1bbef97";
    @TempDir Path photos;
    CollectionAiClient llm = mock(CollectionAiClient.class);
    LocalVisionClient local = mock(LocalVisionClient.class);
    ImageAnalysisRepository repository = mock(ImageAnalysisRepository.class);
    AiProblemRepository problems = mock(AiProblemRepository.class);
    AiProperties properties;
    ImageAnalysisService service;
    byte[] afterBytes;

    @BeforeEach void setup() throws Exception {
        properties = new AiProperties();
        properties.setCollectionProvider(AiProperties.CollectionProvider.OPENROUTER);
        properties.setVisionEngine(AiProperties.VisionEngine.LOCAL);
        properties.getStorage().setPhotosDir(photos.toString());
        service = new ImageAnalysisService(llm, properties, repository, problems, new ObjectMapper(), local);
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", photos.resolve("after.png").toFile());
        var before = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        before.setRGB(0, 0, 0xff0000);
        ImageIO.write(before, "png", photos.resolve("before.png").toFile());
        afterBytes = Files.readAllBytes(photos.resolve("after.png"));
        when(repository.save(any())).thenAnswer(invocation -> {
            ImageAnalysis result = invocation.getArgument(0); result.setId("analysis-1"); return result;
        });
    }

    @Test void localConfigurationNamesTheModelAndItsOwnVersion() {
        var config = service.evaluationConfiguration();
        assertThat(config.provider()).isEqualTo("LOCAL");
        assertThat(config.model()).isEqualTo("proprete_mobilenetv3l@sha256:" + SHA);
        assertThat(config.promptVersion()).isEqualTo("local-cleanliness-v1");
    }

    @Test void onlyAfterPhotoIsSentAndPredictionIsStoredWithoutConfidence() {
        when(local.predict(any())).thenReturn(prediction("PROPRE", 0.02, 0.07, 0.91, null));
        var result = service.analyzeStoredEvidence("/uploads/photos/after.png", "/uploads/photos/before.png", "point", "tour", "driver");
        assertThat(result.getStatus()).isEqualTo("CLEANED");
        assertThat(result.getInputMode()).isEqualTo("BEFORE_AFTER");
        assertThat(result.getProvider()).isEqualTo("LOCAL");
        assertThat(result.getModel()).isEqualTo("proprete_mobilenetv3l@sha256:" + SHA);
        assertThat(result.getPromptVersion()).isEqualTo("local-cleanliness-v1");
        assertThat(result.getConfidence()).isNull();
        assertThat(result.getDecision()).isEqualTo("A_VERIFIER"); // aucun seuil défini
        assertThat(result.getProbabilities()).containsEntry("PROPRE", 0.91).containsEntry("DEBORDEMENT", 0.07);
        assertThat(result.getPredictedClass()).isEqualTo("PROPRE");
        assertThat(result.getModelSha256()).isEqualTo(SHA);
        assertThat(result.getSharpness()).isEqualTo(412.5);
        assertThat(result.getMeanBrightness()).isEqualTo(120.25);
        assertThat(result.getQualityIssue()).isNull();
        assertThat(result.getFailureCategory()).isNull();
        assertThat(result.getDescription()).contains("propre 91 %").contains("À confirmer par un contrôle humain");
        verify(local).predict(argThat(image -> java.util.Arrays.equals(image.bytes(), afterBytes) && "image/png".equals(image.mimeType())));
        verifyNoInteractions(llm, problems);
    }

    @Test void thresholdAllowsAutomaticConfirmationOnlyForPropre() {
        properties.getLocalVision().setPropreThreshold(0.9);
        when(local.predict(any())).thenReturn(prediction("PROPRE", 0.02, 0.07, 0.91, null));
        assertThat(service.analyzeStoredEvidence("/uploads/photos/after.png", null, "p", "t", "d").getDecision())
                .isEqualTo("CONFIRMATION_AUTO");
        when(local.predict(any())).thenReturn(prediction("DEBORDEMENT", 0.02, 0.93, 0.05, null));
        var debordement = service.analyzeStoredEvidence("/uploads/photos/after.png", null, "p", "t", "d");
        assertThat(debordement.getStatus()).isEqualTo("NOT_CLEANED");
        assertThat(debordement.getDecision()).isEqualTo("A_VERIFIER");
    }

    @Test void qualityRefusalIsCannotDetermineButKeepsProbabilities() {
        properties.getLocalVision().setPropreThreshold(0.5);
        when(local.predict(any())).thenReturn(prediction("PROPRE", 0.01, 0.01, 0.98, "TROP_SOMBRE"));
        var result = service.analyzeStoredEvidence("/uploads/photos/after.png", null, "p", "t", "d");
        assertThat(result.getStatus()).isEqualTo("CANNOT_DETERMINE");
        assertThat(result.getDecision()).isEqualTo("A_VERIFIER");
        assertThat(result.getQualityIssue()).isEqualTo("TROP_SOMBRE");
        assertThat(result.getProbabilities()).containsEntry("PROPRE", 0.98);
        assertThat(result.getDescription()).startsWith("Photo APRÈS trop sombre");
    }

    @Test void serviceFailureIsUnavailableNeverCleaned() {
        when(local.predict(any())).thenThrow(new AiServiceException("Service local injoignable.", "PROVIDER_UNAVAILABLE", null, null));
        var result = service.analyzeStoredEvidence("/uploads/photos/after.png", null, "p", "t", "d");
        assertThat(result.getStatus()).isEqualTo("ANALYSIS_UNAVAILABLE");
        assertThat(result.getFailureCategory()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(result.getProbabilities()).isNull();
        assertThat(result.getDecision()).isNull();
        assertThat(result.getConfidence()).isNull();
        verifyNoInteractions(llm, problems);
    }

    @Test void missingLocalClientIsNotConfigured() {
        var withoutClient = new ImageAnalysisService(llm, properties, repository, problems, new ObjectMapper());
        var result = withoutClient.analyzeStoredEvidence("/uploads/photos/after.png", null, "p", "t", "d");
        assertThat(result.getStatus()).isEqualTo("ANALYSIS_UNAVAILABLE");
        assertThat(result.getFailureCategory()).isEqualTo("NOT_CONFIGURED");
        verifyNoInteractions(llm);
    }

    @Test void maliciousPhotoReferenceIsNotSentToLocalService() {
        var result = service.analyzeStoredEvidence("/uploads/photos/../../secret.png", null, "p", "t", "d");
        assertThat(result.getStatus()).isEqualTo("ANALYSIS_UNAVAILABLE");
        verifyNoInteractions(local);
    }

    @Test void analysesOfAnotherEngineAreNeverReused() {
        properties.setVisionEngine(AiProperties.VisionEngine.LLM);
        var llmAnalysis = saved(service.evaluationConfiguration());
        properties.setVisionEngine(AiProperties.VisionEngine.LOCAL);
        when(repository.findById("saved")).thenReturn(Optional.of(llmAnalysis));
        assertThat(service.reusableVisitAnalysis("saved", "/uploads/photos/after.png", null, "point", "tour", "driver")).isEmpty();

        var localAnalysis = saved(service.evaluationConfiguration());
        when(repository.findById("saved")).thenReturn(Optional.of(localAnalysis));
        assertThat(service.reusableVisitAnalysis("saved", "/uploads/photos/after.png", null, "point", "tour", "driver")).containsSame(localAnalysis);
        properties.setVisionEngine(AiProperties.VisionEngine.LLM);
        assertThat(service.reusableVisitAnalysis("saved", "/uploads/photos/after.png", null, "point", "tour", "driver")).isEmpty();
        verifyNoInteractions(llm, local);
    }

    @Test void evaluationUsesLocalVersion() throws Exception {
        when(local.predict(any())).thenReturn(prediction("CANNOT_DETERMINE", 0.8, 0.1, 0.1, null));
        var config = service.evaluationConfiguration();
        var after = new MockMultipartFile("afterPhoto", "after.png", "image/png", afterBytes);
        var result = service.evaluateEvidence(after, null, config.model(), config.promptVersion());
        assertThat(result.getStatus()).isEqualTo("CANNOT_DETERMINE");
        assertThat(result.getDescription()).contains("photo difficile à interpréter");
        assertThatThrownBy(() -> service.evaluateEvidence(after, null, config.model(), "point-cleanliness-v1"))
                .hasMessageContaining("409");
        verifyNoInteractions(llm);
    }

    private static ImageAnalysis saved(ImageAnalysisService.EvaluationConfiguration configuration) {
        return ImageAnalysis.builder().id("saved").status("NOT_CLEANED")
                .description("Déchets visibles.").tourneeId("tour").pointCollecteId("point").chauffeurId("driver")
                .afterPhotoUrl("/uploads/photos/after.png").provider(configuration.provider())
                .model(configuration.model()).promptVersion(configuration.promptVersion()).build();
    }

    private static LocalVisionClient.Prediction prediction(String predicted, double c, double d, double p, String issue) {
        Map<String, Double> probabilities = new LinkedHashMap<>();
        probabilities.put("CANNOT_DETERMINE", c);
        probabilities.put("DEBORDEMENT", d);
        probabilities.put("PROPRE", p);
        return new LocalVisionClient.Prediction(probabilities, predicted, 412.5, 120.25, issue, "proprete_mobilenetv3l", SHA);
    }
}
