package com.municipalite.gestiontournees.ai.vision.narrative;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.common.AiServiceException;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.vision.model.ImageAnalysis;
import com.municipalite.gestiontournees.ai.vision.repository.ImageAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NarrativeServiceTest {
    private static final String TEMPLATE_TEXT = "Analyse automatique locale (modèle proprete_mobilenetv3l) de la photo APRÈS : "
            + "point probablement propre. Probabilités : propre 91 %, débordement 7 %, indéterminé 2 %. À confirmer par un contrôle humain.";
    private static final String GOOD = "{\"texte\":\"Analyse automatique : le point est probablement propre à 91 %. "
            + "Ce résultat reste à confirmer par un contrôle humain.\"}";
    private static final String BAD = "{\"texte\":\"On voit un point propre à 91 %.\"}";

    ImageAnalysisRepository repository = mock(ImageAnalysisRepository.class);
    AiProperties properties;
    FakeGenerator generator;
    NarrativeService service;
    ImageAnalysis analysis;

    /** Simulated generator: records each prompt and answers from a queue of suppliers. */
    static class FakeGenerator implements NarrativeGenerator {
        final List<String> prompts = Collections.synchronizedList(new ArrayList<>());
        final Deque<Supplier<String>> answers = new ArrayDeque<>();
        @Override public String generate(String prompt) {
            prompts.add(prompt);
            Supplier<String> next;
            synchronized (answers) { next = answers.size() > 1 ? answers.poll() : answers.peek(); }
            return next.get();
        }
        @Override public String modelId() { return "GROQ:qwen/qwen3.8-27b"; }
        FakeGenerator then(Supplier<String> answer) { answers.add(answer); return this; }
    }

    @BeforeEach void setup() {
        properties = new AiProperties();
        properties.setVisionNarration(AiProperties.VisionNarration.LLM);
        properties.setVisionNarrationTimeoutSeconds(1);
        generator = new FakeGenerator();
        service = new NarrativeService(repository, properties, generator, new NarrativeValidator(), new ObjectMapper());
        Map<String, Double> probabilities = new LinkedHashMap<>();
        probabilities.put("CANNOT_DETERMINE", 0.02);
        probabilities.put("DEBORDEMENT", 0.07);
        probabilities.put("PROPRE", 0.91);
        analysis = ImageAnalysis.builder().id("a1").provider("LOCAL").status("CLEANED").predictedClass("PROPRE")
                .probabilities(probabilities).decision("A_VERIFIER").sharpness(412.46).meanBrightness(120.25)
                .inputMode("BEFORE_AFTER").description(TEMPLATE_TEXT)
                // Fields that must never reach the language model:
                .tourneeId("tour-secret-42").pointCollecteId("point-secret").chauffeurId("driver-secret")
                .afterPhotoUrl("/uploads/photos/secret-after.jpg").beforePhotoUrl("/uploads/photos/secret-before.jpg")
                .correctionChauffeur("NOT_CLEANED").reviewNotes("note libre du superviseur").build();
        when(repository.findById("a1")).thenAnswer(i -> Optional.of(analysis));
        when(repository.save(any())).thenAnswer(i -> { analysis = i.getArgument(0); return analysis; });
    }

    @Test void templateModeNeverCallsALanguageModel() {
        properties.setVisionNarration(AiProperties.VisionNarration.TEMPLATE);
        var result = service.narrate("a1");
        assertThat(result.getNarrative()).isEqualTo(TEMPLATE_TEXT);
        assertThat(result.getNarrativeSource()).isEqualTo("TEMPLATE");
        assertThat(result.getNarrativeFailureCategory()).isNull();
        assertThat(result.getDescription()).isEqualTo(TEMPLATE_TEXT);
        assertThat(generator.prompts).isEmpty();
    }

    @Test void acceptedTextIsStoredWithoutChangingStatusDecisionOrDescription() {
        generator.then(() -> GOOD);
        var result = service.narrate("a1");
        assertThat(result.getNarrativeSource()).isEqualTo("LLM");
        assertThat(result.getNarrative()).startsWith("Analyse automatique : le point est probablement propre à 91 %.");
        assertThat(result.getNarrativeModel()).isEqualTo("GROQ:qwen/qwen3.8-27b");
        assertThat(result.getNarrativePromptVersion()).isEqualTo("vision-narrative-v1");
        assertThat(result.getNarrativeGeneratedAt()).isNotNull();
        assertThat(result.getNarrativeFailureCategory()).isNull();
        assertThat(result.getDescription()).isEqualTo(TEMPLATE_TEXT);
        assertThat(result.getStatus()).isEqualTo("CLEANED");
        assertThat(result.getDecision()).isEqualTo("A_VERIFIER");
        assertThat(generator.prompts).hasSize(1);
    }

    @Test void rejectedTextIsRetriedOnceWithRulesButWithoutTheRejectedText() {
        generator.then(() -> BAD).then(() -> GOOD);
        var result = service.narrate("a1");
        assertThat(result.getNarrativeSource()).isEqualTo("LLM");
        assertThat(generator.prompts).hasSize(2);
        assertThat(generator.prompts.get(0)).doesNotContain("refusée");
        assertThat(generator.prompts.get(1)).contains("refusée").doesNotContain("On voit un point");
    }

    @Test void twoRejectedTextsFallBackToTheTemplate() {
        generator.then(() -> BAD);
        var result = service.narrate("a1");
        assertThat(generator.prompts).hasSize(2);
        assertThat(result.getNarrativeSource()).isEqualTo("TEMPLATE");
        assertThat(result.getNarrative()).isEqualTo(TEMPLATE_TEXT);
        assertThat(result.getNarrativeFailureCategory()).startsWith("VALIDATION_V");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROVIDER_HTTP_ERROR", "PROVIDER_TIMEOUT", "NOT_CONFIGURED", "PROVIDER_UNAVAILABLE"})
    void providerFailuresFallBackWithoutRetry(String category) {
        Integer status = "PROVIDER_HTTP_ERROR".equals(category) ? 429 : null;
        generator.then(() -> { throw new AiServiceException("indisponible", category, status, null); });
        var result = service.narrate("a1");
        assertThat(generator.prompts).hasSize(1);
        assertThat(result.getNarrativeSource()).isEqualTo("TEMPLATE");
        assertThat(result.getNarrativeFailureCategory()).isEqualTo(category);
        assertThat(result.getNarrative()).isEqualTo(TEMPLATE_TEXT);
    }

    @Test void slowGeneratorTimesOutWithoutRetry() {
        generator.then(() -> { sleep(3000); return GOOD; });
        var result = service.narrate("a1");
        assertThat(generator.prompts).hasSize(1);
        assertThat(result.getNarrativeFailureCategory()).isEqualTo("PROVIDER_TIMEOUT");
        assertThat(result.getNarrativeSource()).isEqualTo("TEMPLATE");
    }

    @Test void malformedAnswerFallsBackWithoutRetry() {
        generator.then(() -> "{\"autre\":1}");
        assertThat(service.narrate("a1").getNarrativeFailureCategory()).isEqualTo("INVALID_PROVIDER_RESPONSE");
        assertThat(generator.prompts).hasSize(1);
    }

    @Test void llmNarrativeIsCachedButTemplateFallbackCanBeRetried() {
        generator.then(() -> { throw new AiServiceException("x", "PROVIDER_TIMEOUT", null, null); });
        assertThat(service.narrate("a1").getNarrativeSource()).isEqualTo("TEMPLATE");
        generator.answers.clear();
        generator.then(() -> GOOD);
        assertThat(service.narrate("a1").getNarrativeSource()).isEqualTo("LLM");
        assertThat(service.narrate("a1").getNarrativeSource()).isEqualTo("LLM");
        assertThat(generator.prompts).hasSize(2);
    }

    @Test void nonLocalOrIncompleteAnalysesAreRefused() {
        analysis.setProvider("GROQ");
        assertThatThrownBy(() -> service.narrate("a1")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        analysis.setProvider("LOCAL");
        analysis.setProbabilities(null);
        assertThatThrownBy(() -> service.narrate("a1")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.narrate("missing")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        assertThat(generator.prompts).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test void simultaneousRequestsMakeOnlyOneCall() throws Exception {
        properties.setVisionNarrationTimeoutSeconds(5);
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        generator.then(() -> { started.countDown(); await(release); return GOOD; });
        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<ImageAnalysis> first = pool.submit(() -> service.narrate("a1"));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service.narrate("a1")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).getNarrativeSource()).isEqualTo("LLM");
        } finally {
            pool.shutdownNow();
        }
        assertThat(generator.prompts).hasSize(1);
    }

    @Test void promptContainsOnlyStructuredFacts() {
        generator.then(() -> GOOD);
        service.narrate("a1");
        String prompt = generator.prompts.get(0);
        assertThat(prompt).contains("\"classe_predite\":\"propre\"").contains("\"propre\":91").contains("\"débordement\":7")
                .contains("\"indéterminé\":2").contains("\"decision\":\"à vérifier\"").contains("\"probleme_qualite\":null")
                .contains("\"nettete\":412.5").contains("\"luminosite\":120.3")
                .contains("photos AVANT et APRÈS fournies, seule la photo APRÈS est analysée");
        assertThat(prompt).doesNotContain("secret").doesNotContain("a1\"").doesNotContain("uploads")
                .doesNotContain("note libre").doesNotContain("NOT_CLEANED").doesNotContain(TEMPLATE_TEXT);
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
