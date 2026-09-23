package com.municipalite.gestiontournees.ai.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.common.AiServiceException;
import com.municipalite.gestiontournees.ai.common.CollectionAiClient;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.vision.model.ImageAnalysis;
import com.municipalite.gestiontournees.ai.vision.repository.*;
import com.municipalite.gestiontournees.ai.vision.service.ImageAnalysisService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageAnalysisServiceTest {
    @TempDir Path photos;
    CollectionAiClient openRouter = mock(CollectionAiClient.class);
    ImageAnalysisRepository repository = mock(ImageAnalysisRepository.class);
    AiProblemRepository problems = mock(AiProblemRepository.class);
    ImageAnalysisService service;
    AiProperties properties;

    @BeforeEach void setup() throws Exception {
        properties = new AiProperties();
        properties.setCollectionProvider(AiProperties.CollectionProvider.OPENROUTER);
        properties.getStorage().setPhotosDir(photos.toString());
        service = new ImageAnalysisService(openRouter, properties, repository, problems, new ObjectMapper());
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", photos.resolve("after.png").toFile());
        var before = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        before.setRGB(0, 0, 0xff0000);
        ImageIO.write(before, "png", photos.resolve("before.png").toFile());
        when(repository.save(any())).thenAnswer(invocation -> {
            ImageAnalysis result = invocation.getArgument(0); result.setId("analysis-1"); return result;
        });
    }

    @Test void afterOnlyIsSufficientAndHasNoInventedConfidence() {
        when(openRouter.analyzeImages(anyList(), anyString())).thenReturn("{\"status\":\"CLEANED\",\"description\":\"Sol visible propre.\"}");
        ImageAnalysis result = service.analyzeStoredEvidence("/uploads/photos/after.png", null, "point", "tour", "driver");
        assertThat(result.getStatus()).isEqualTo("CLEANED");
        assertThat(result.getInputMode()).isEqualTo("AFTER_ONLY");
        assertThat(result.getConfidence()).isNull();
        assertThat(result.getPromptVersion()).isEqualTo("point-cleanliness-v1");
        verifyNoInteractions(problems);
        verify(openRouter).analyzeImages(argThat(images -> images.size() == 1), contains("Une seule image"));
    }

    @Test void pairedEvidenceUsesBeforeThenAfter() {
        when(openRouter.analyzeImages(anyList(), anyString())).thenReturn("{\"status\":\"NOT_CLEANED\",\"description\":\"Sacs visibles.\"}");
        ImageAnalysis result = service.analyzeStoredEvidence("/uploads/photos/after.png", "/uploads/photos/before.png", "point", "tour", "driver");
        assertThat(result.getInputMode()).isEqualTo("BEFORE_AFTER");
        verify(openRouter).analyzeImages(argThat(images -> images.size() == 2), contains("première image est AVANT"));
    }

    @Test void outageAndMalformedResponsesAreUnavailableNeverCleaned() {
        when(openRouter.analyzeImages(anyList(), anyString())).thenThrow(new AiServiceException("Offline"));
        assertThat(service.analyzeStoredEvidence("/uploads/photos/after.png", null, "p", "t", "d").getStatus()).isEqualTo("ANALYSIS_UNAVAILABLE");
        doReturn("{\"status\":\"NORMAL\"}").when(openRouter).analyzeImages(anyList(), anyString());
        assertThat(service.analyzeStoredEvidence("/uploads/photos/after.png", null, "p", "t", "d").getStatus()).isEqualTo("ANALYSIS_UNAVAILABLE");
    }

    @Test void openRouterUsesConfiguredModelAndPreservesAfterOnlyAndPairedOrder() throws Exception {
        when(openRouter.analyzeImages(anyList(), anyString())).thenReturn("{\"status\":\"NOT_CLEANED\",\"description\":\"Déchets visibles au sol.\"}");
        var config = service.evaluationConfiguration();
        var afterBytes = Files.readAllBytes(photos.resolve("after.png"));
        var beforeBytes = Files.readAllBytes(photos.resolve("before.png"));
        var after = new MockMultipartFile("afterPhoto", "after.png", "image/png", afterBytes);
        var single = service.evaluateEvidence(after, null, config.model(), config.promptVersion());
        var pair = service.analyzeStoredEvidence("/uploads/photos/after.png", "/uploads/photos/before.png", "p", "t", "d");
        assertThat(config.provider()).isEqualTo("OPENROUTER");
        assertThat(single.getProvider()).isEqualTo("OPENROUTER");
        assertThat(single.getModel()).isEqualTo("qwen/qwen3.8-27b:free");
        assertThat(single.getStatus()).isEqualTo("NOT_CLEANED");
        assertThat(single.getFailureCategory()).isNull();
        assertThat(pair.getInputMode()).isEqualTo("BEFORE_AFTER");
        assertThat(pair.getStatus()).isEqualTo("NOT_CLEANED");
        verify(openRouter).analyzeImages(argThat(images -> images.size() == 1 && java.util.Arrays.equals(images.get(0).bytes(), afterBytes)), contains("Une seule image"));
        verify(openRouter).analyzeImages(argThat(images -> images.size() == 2 && java.util.Arrays.equals(images.get(0).bytes(), beforeBytes)
                && java.util.Arrays.equals(images.get(1).bytes(), afterBytes)), contains("première image est AVANT"));
        verifyNoInteractions(problems);
    }

    @Test void openRouterFailureStoresSafeDiagnostics() {
        when(openRouter.analyzeImages(anyList(), anyString())).thenThrow(new AiServiceException("private-provider-text", "PROVIDER_HTTP_ERROR", 429, null));
        var result = service.analyzeStoredEvidence("/uploads/photos/after.png", null, "p", "t", "d");
        assertThat(result.getStatus()).isEqualTo("ANALYSIS_UNAVAILABLE");
        assertThat(result.getFailureCategory()).isEqualTo("PROVIDER_HTTP_ERROR");
        assertThat(result.getProviderHttpStatus()).isEqualTo(429);
        assertThat(result.getDescription()).contains("limite temporaire").doesNotContain("private-provider-text");
        assertThat(result.getAfterPhotoUrl()).isEqualTo("/uploads/photos/after.png");
        verifyNoInteractions(problems);
    }

    @Test void malformedAnalysisHasDistinctSafeFailureCategory() {
        when(openRouter.analyzeImages(anyList(), anyString())).thenReturn("{invalid-json");
        var result = service.analyzeStoredEvidence("/uploads/photos/after.png", null, "p", "t", "d");
        assertThat(result.getStatus()).isEqualTo("ANALYSIS_UNAVAILABLE");
        assertThat(result.getFailureCategory()).isEqualTo("INVALID_ANALYSIS_RESPONSE");
        assertThat(result.getProviderHttpStatus()).isNull();
        assertThat(result.getDescription()).doesNotContain("invalid-json");
    }

    @Test void maliciousPhotoReferenceIsNotSentToProvider() {
        assertThat(service.analyzeStoredEvidence("/uploads/photos/../../secret.png", null, "p", "t", "d").getStatus()).isEqualTo("ANALYSIS_UNAVAILABLE");
        verifyNoInteractions(openRouter);
    }

    @Test void humanCorrectionAndReviewPreserveOriginalPrediction() {
        ImageAnalysis analysis = ImageAnalysis.builder().id("a").status("NOT_CLEANED").build();
        when(repository.findById("a")).thenReturn(Optional.of(analysis));
        service.confirmOrCorrect("a", false, "CLEANED");
        service.review("a", "OBSTRUCTED", "Accès bloqué", "supervisor");
        assertThat(analysis.getStatus()).isEqualTo("NOT_CLEANED");
        assertThat(analysis.getCorrectionChauffeur()).isEqualTo("CLEANED");
        assertThat(analysis.getReviewedLabel()).isEqualTo("OBSTRUCTED");
    }

    @Test void unchangedVisitReusesOriginalUncertainAssessmentWithoutHumanRelabelling() {
        var previous = savedVisitAssessment();
        previous.setStatus("CANNOT_DETERMINE");
        previous.setReviewedLabel("CLEANED");
        previous.setCorrectionChauffeur("OBSTRUCTED");
        when(repository.findById("saved")).thenReturn(Optional.of(previous));
        var reused = service.reusableVisitAnalysis("saved", "/uploads/photos/after.png", null,
                "point", "tour", "driver");
        assertThat(reused).containsSame(previous);
        assertThat(reused.orElseThrow().getStatus()).isEqualTo("CANNOT_DETERMINE");
        verifyNoInteractions(openRouter, problems);
        verify(repository, never()).save(any());
    }

    @Test void unavailableChangedEvidenceAndDifferentConfigurationCannotBeReused() {
        List<java.util.function.Consumer<ImageAnalysis>> invalidations = List.of(
                a -> a.setStatus("ANALYSIS_UNAVAILABLE"),
                a -> a.setStatus(null),
                a -> a.setFailureCategory("PROVIDER_TIMEOUT"),
                a -> a.setAfterPhotoUrl("/uploads/photos/replacement.png"),
                a -> a.setBeforePhotoUrl("/uploads/photos/before.png"),
                a -> a.setTourneeId("another-tour"),
                a -> a.setPointCollecteId("another-point"),
                a -> a.setChauffeurId("another-driver"),
                a -> a.setProvider("ANOTHER_PROVIDER"),
                a -> a.setModel("another/model:free"),
                a -> a.setPromptVersion("old-prompt"));
        for (var invalidate : invalidations) {
            var previous = savedVisitAssessment();
            invalidate.accept(previous);
            when(repository.findById("saved")).thenReturn(Optional.of(previous));
            assertThat(service.reusableVisitAnalysis("saved", "/uploads/photos/after.png", null,
                    "point", "tour", "driver")).isEmpty();
        }
        assertThat(service.reusableVisitAnalysis(null, "/uploads/photos/after.png", null,
                "point", "tour", "driver")).isEmpty();
        verifyNoInteractions(openRouter, problems);
    }

    @Test void evaluationAlwaysCallsProviderEvenForIdenticalImages() throws Exception {
        when(openRouter.analyzeImages(anyList(), anyString()))
                .thenReturn("{\"status\":\"CANNOT_DETERMINE\",\"description\":\"Cadrage insuffisant.\"}");
        var config = service.evaluationConfiguration();
        var after = new MockMultipartFile("afterPhoto", "after.png", "image/png", Files.readAllBytes(photos.resolve("after.png")));
        service.evaluateEvidence(after, null, config.model(), config.promptVersion());
        service.evaluateEvidence(after, null, config.model(), config.promptVersion());
        verify(openRouter, times(2)).analyzeImages(anyList(), anyString());
        verify(repository, never()).findById(anyString());
    }

    private ImageAnalysis savedVisitAssessment() {
        var configuration = service.evaluationConfiguration();
        return ImageAnalysis.builder().id("saved").status("NOT_CLEANED")
                .description("Déchets visibles.").tourneeId("tour").pointCollecteId("point").chauffeurId("driver")
                .afterPhotoUrl("/uploads/photos/after.png").provider(configuration.provider())
                .model(configuration.model()).promptVersion(configuration.promptVersion()).build();
    }

    @Test void evaluationUsesSamePromptAndBothInputModesWithoutTourMutation() throws Exception {
        when(openRouter.analyzeImages(anyList(), anyString())).thenReturn("{\"status\":\"CLEANED\",\"description\":\"Sol visible propre.\"}");
        var config = service.evaluationConfiguration();
        var after = new MockMultipartFile("afterPhoto", "after.png", "image/png", Files.readAllBytes(photos.resolve("after.png")));
        var before = new MockMultipartFile("beforePhoto", "before.png", "image/png", Files.readAllBytes(photos.resolve("before.png")));
        var single = service.evaluateEvidence(after, null, config.model(), config.promptVersion());
        var pair = service.evaluateEvidence(after, before, config.model(), config.promptVersion());
        assertThat(single.getInputMode()).isEqualTo("AFTER_ONLY");
        assertThat(pair.getInputMode()).isEqualTo("BEFORE_AFTER");
        assertThat(pair.getBeforePhotoUrl()).startsWith("/uploads/photos/");
        assertThat(pair.getAfterPhotoUrl()).startsWith("/uploads/photos/");
        assertThat(pair.getTourneeId()).isNull();
        assertThat(pair.getPointCollecteId()).isNull();
        assertThat(pair.getReviewedLabel()).isNull();
        assertThat(single.getModel()).isEqualTo(pair.getModel()).isEqualTo(config.model());
        assertThat(single.getPromptVersion()).isEqualTo(pair.getPromptVersion()).isEqualTo(config.promptVersion());
        verify(openRouter).analyzeImages(argThat(images -> images.size() == 1), contains("Une seule image"));
        verify(openRouter).analyzeImages(argThat(images -> images.size() == 2), contains("première image est AVANT"));
        verifyNoInteractions(problems);
    }

    @Test void changedEvaluationConfigurationAndEmptyPhotosDoNotCallProvider() {
        var empty = new MockMultipartFile("afterPhoto", new byte[0]);
        var config = service.evaluationConfiguration();
        assertThatThrownBy(() -> service.evaluateEvidence(empty, null, "different-model", config.promptVersion()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThatThrownBy(() -> service.evaluateEvidence(empty, null, config.model(), config.promptVersion()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        verifyNoInteractions(openRouter, repository);
    }
}
