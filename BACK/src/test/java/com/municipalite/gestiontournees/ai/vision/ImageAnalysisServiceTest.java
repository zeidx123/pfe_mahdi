package com.municipalite.gestiontournees.ai.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.common.AiServiceException;
import com.municipalite.gestiontournees.ai.common.GeminiClient;
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
    GeminiClient gemini = mock(GeminiClient.class);
    ImageAnalysisRepository repository = mock(ImageAnalysisRepository.class);
    AiProblemRepository problems = mock(AiProblemRepository.class);
    ImageAnalysisService service;

    @BeforeEach void setup() throws Exception {
        AiProperties properties = new AiProperties();
        properties.getStorage().setPhotosDir(photos.toString());
        service = new ImageAnalysisService(gemini, properties, repository, problems, new ObjectMapper());
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", photos.resolve("after.png").toFile());
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", photos.resolve("before.png").toFile());
        when(repository.save(any())).thenAnswer(invocation -> {
            ImageAnalysis result = invocation.getArgument(0); result.setId("analysis-1"); return result;
        });
    }

    @Test void afterOnlyIsSufficientAndHasNoInventedConfidence() {
        when(gemini.analyzeImages(anyList(), anyString())).thenReturn("{\"status\":\"CLEANED\",\"description\":\"Sol visible propre.\"}");
        ImageAnalysis result = service.analyzeStoredEvidence("/uploads/photos/after.png", null, "point", "tour", "driver");
        assertThat(result.getStatus()).isEqualTo("CLEANED");
        assertThat(result.getInputMode()).isEqualTo("AFTER_ONLY");
        assertThat(result.getConfidence()).isNull();
        assertThat(result.getPromptVersion()).isEqualTo("point-cleanliness-v1");
        verifyNoInteractions(problems);
        verify(gemini).analyzeImages(argThat(images -> images.size() == 1), contains("Une seule image"));
    }

    @Test void pairedEvidenceUsesBeforeThenAfter() {
        when(gemini.analyzeImages(anyList(), anyString())).thenReturn("{\"status\":\"NOT_CLEANED\",\"description\":\"Sacs visibles.\"}");
        ImageAnalysis result = service.analyzeStoredEvidence("/uploads/photos/after.png", "/uploads/photos/before.png", "point", "tour", "driver");
        assertThat(result.getInputMode()).isEqualTo("BEFORE_AFTER");
        verify(gemini).analyzeImages(argThat(images -> images.size() == 2), contains("première image est AVANT"));
    }

    @Test void outageAndMalformedResponsesAreUnavailableNeverCleaned() {
        when(gemini.analyzeImages(anyList(), anyString())).thenThrow(new AiServiceException("Offline"));
        assertThat(service.analyzeStoredEvidence("/uploads/photos/after.png", null, "p", "t", "d").getStatus()).isEqualTo("ANALYSIS_UNAVAILABLE");
        doReturn("{\"status\":\"NORMAL\"}").when(gemini).analyzeImages(anyList(), anyString());
        assertThat(service.analyzeStoredEvidence("/uploads/photos/after.png", null, "p", "t", "d").getStatus()).isEqualTo("ANALYSIS_UNAVAILABLE");
    }

    @Test void maliciousPhotoReferenceIsNotSentToProvider() {
        assertThat(service.analyzeStoredEvidence("/uploads/photos/../../secret.png", null, "p", "t", "d").getStatus()).isEqualTo("ANALYSIS_UNAVAILABLE");
        verifyNoInteractions(gemini);
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

    @Test void evaluationUsesSamePromptAndBothInputModesWithoutTourMutation() throws Exception {
        when(gemini.analyzeImages(anyList(), anyString())).thenReturn("{\"status\":\"CLEANED\",\"description\":\"Sol visible propre.\"}");
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
        verify(gemini).analyzeImages(argThat(images -> images.size() == 1), contains("Une seule image"));
        verify(gemini).analyzeImages(argThat(images -> images.size() == 2), contains("première image est AVANT"));
        verifyNoInteractions(problems);
    }

    @Test void changedEvaluationConfigurationAndEmptyPhotosDoNotCallProvider() {
        var empty = new MockMultipartFile("afterPhoto", new byte[0]);
        var config = service.evaluationConfiguration();
        assertThatThrownBy(() -> service.evaluateEvidence(empty, null, "different-model", config.promptVersion()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThatThrownBy(() -> service.evaluateEvidence(empty, null, config.model(), config.promptVersion()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
        verifyNoInteractions(gemini, repository);
    }
}
