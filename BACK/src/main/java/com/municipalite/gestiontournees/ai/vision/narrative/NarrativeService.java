package com.municipalite.gestiontournees.ai.vision.narrative;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.common.AiServiceException;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.vision.model.ImageAnalysis;
import com.municipalite.gestiontournees.ai.vision.repository.ImageAnalysisRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Optional supervisor summary of a LOCAL verdict. The template description stays the reference and never changes;
 * the narrative never changes the status, the decision or any threshold. Logs carry no prompt and no generated text.
 */
@Service
@Slf4j
public class NarrativeService {
    public static final String PROMPT_VERSION = "vision-narrative-v1";
    public static final String SOURCE_LLM = "LLM";
    public static final String SOURCE_TEMPLATE = "TEMPLATE";
    static final String PROMPT_RESOURCE = "prompts/vision-narrative-v1.txt";
    private static final List<String> CLASSES = List.of("CANNOT_DETERMINE", "DEBORDEMENT", "PROPRE");
    private static final Map<String, String> RULES = Map.of(
            "V1", "2 à 4 phrases, 600 caractères au maximum",
            "V2", "aucun nombre autre que ceux fournis",
            "V3", "aucune description de l'image ni mot interdit (objets, chauffeur, preuve, certitude, incident, faute)",
            "V4", "indiquer que l'analyse est automatique et à confirmer par un contrôle humain",
            "V5", "citer le pourcentage de la classe prédite suivi de « % » et, si besoin, le problème de qualité de la photo",
            "V6", "aucun caractère de mise en forme (* # { } `)",
            "V7", "aucun lien");
    private final ImageAnalysisRepository repository;
    private final AiProperties aiProperties;
    private final NarrativeGenerator generator;
    private final NarrativeValidator validator;
    private final ObjectMapper objectMapper;
    private final String promptTemplate;
    // One backend process serves the PFE: prevent duplicate model calls for the same analysis (as in TourneeService).
    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "vision-narrative");
        thread.setDaemon(true);
        return thread;
    });

    public NarrativeService(ImageAnalysisRepository repository, AiProperties aiProperties, NarrativeGenerator generator,
                            NarrativeValidator validator, @Qualifier("aiObjectMapper") ObjectMapper objectMapper) {
        this.repository = repository;
        this.aiProperties = aiProperties;
        this.generator = generator;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.promptTemplate = loadPrompt();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public ImageAnalysis narrate(String analysisId) {
        ImageAnalysis analysis = repository.findById(analysisId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analyse introuvable."));
        NarrativeFacts facts = facts(analysis);
        if (SOURCE_LLM.equals(analysis.getNarrativeSource()) && analysis.getNarrative() != null) return analysis;
        if (aiProperties.getVisionNarration() != AiProperties.VisionNarration.LLM) {
            if (SOURCE_TEMPLATE.equals(analysis.getNarrativeSource()) && analysis.getNarrative() != null) return analysis;
            return save(analysisId, analysis.getDescription(), SOURCE_TEMPLATE, null, null, null);
        }
        if (!inProgress.add(analysisId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Une rédaction est déjà en cours pour cette analyse. Réessayez dans un instant.");
        }
        try {
            return generate(analysisId, analysis, facts);
        } finally {
            inProgress.remove(analysisId);
        }
    }

    private ImageAnalysis generate(String analysisId, ImageAnalysis analysis, NarrativeFacts facts) {
        long start = System.nanoTime();
        int attempts = 0;
        String text = null;
        String failure = null;
        List<String> violations = List.of();
        try {
            while (attempts < 2 && text == null) {
                attempts++;
                String candidate = call(prompt(facts, violations));
                violations = validator.validate(candidate, facts);
                if (violations.isEmpty()) text = NarrativeValidator.trim(candidate);
            }
            if (text == null) failure = "VALIDATION_" + violations.get(0);
        } catch (AiServiceException e) {
            // HTTP error, timeout or rate limit: no retry, immediate fallback.
            failure = e.getCategory() == null ? "PROVIDER_UNAVAILABLE" : e.getCategory();
        }
        log.info("Vision narrative: source={} attempts={} failureCategory={} latencyMs={}",
                text != null ? SOURCE_LLM : SOURCE_TEMPLATE, attempts, failure, (System.nanoTime() - start) / 1_000_000);
        return text != null
                ? save(analysisId, text, SOURCE_LLM, generator.modelId(), null, PROMPT_VERSION)
                : save(analysisId, analysis.getDescription(), SOURCE_TEMPLATE, generator.modelId(), failure, PROMPT_VERSION);
    }

    String prompt(NarrativeFacts facts, List<String> violations) {
        StringBuilder prompt = new StringBuilder(promptTemplate.strip()).append("\n\nDonnées :\n");
        try {
            prompt.append(objectMapper.writeValueAsString(facts.forModel()));
        } catch (JsonProcessingException e) {
            throw new AiServiceException("Données de rédaction invalides.", "INVALID_PROMPT", null, null);
        }
        if (!violations.isEmpty()) {
            // Reminder of the violated rules only; the rejected text is never sent back.
            prompt.append("\n\nTa réponse précédente a été refusée. Règles à respecter absolument : ");
            prompt.append(String.join(" ; ", violations.stream().map(RULES::get).toList())).append('.');
        }
        return prompt.toString();
    }

    private String call(String prompt) {
        int timeout = Math.max(1, Math.min(120, aiProperties.getVisionNarrationTimeoutSeconds()));
        Future<String> future = executor.submit(() -> generator.generate(prompt));
        try {
            JsonNode answer = objectMapper.readTree(future.get(timeout, TimeUnit.SECONDS));
            if (answer == null || !answer.path("texte").isTextual()) {
                throw new AiServiceException("Réponse de rédaction invalide.", "INVALID_PROVIDER_RESPONSE", null, null);
            }
            return answer.path("texte").asText();
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new AiServiceException("Le délai de rédaction est dépassé.", "PROVIDER_TIMEOUT", null, null);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AiServiceException failure) throw failure;
            throw new AiServiceException("Le service de rédaction est indisponible.", "PROVIDER_UNAVAILABLE", null, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiServiceException("La rédaction a été interrompue.", "PROVIDER_UNAVAILABLE", null, null);
        } catch (JsonProcessingException e) {
            throw new AiServiceException("Réponse de rédaction illisible.", "INVALID_PROVIDER_RESPONSE", null, null);
        }
    }

    /** Only the structured model outputs are used; identifiers, names, coordinates and free text are never read. */
    NarrativeFacts facts(ImageAnalysis analysis) {
        Map<String, Double> p = analysis.getProbabilities();
        if (!"LOCAL".equals(analysis.getProvider()) || p == null || CLASSES.stream().anyMatch(c -> p.get(c) == null)
                || !CLASSES.contains(analysis.getPredictedClass())
                || analysis.getSharpness() == null || analysis.getMeanBrightness() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le résumé rédigé n'est disponible que pour une analyse réussie du modèle local.");
        }
        return new NarrativeFacts(analysis.getPredictedClass(), (int) Math.round(p.get("PROPRE") * 100),
                (int) Math.round(p.get("DEBORDEMENT") * 100), (int) Math.round(p.get("CANNOT_DETERMINE") * 100),
                analysis.getQualityIssue(), NarrativeFacts.oneDecimal(analysis.getSharpness()),
                NarrativeFacts.oneDecimal(analysis.getMeanBrightness()), analysis.getDecision(), analysis.getInputMode());
    }

    private ImageAnalysis save(String analysisId, String narrative, String source, String model, String failure, String version) {
        // Re-read so that a concurrent review saved meanwhile is not overwritten.
        ImageAnalysis fresh = repository.findById(analysisId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analyse introuvable."));
        fresh.setNarrative(narrative);
        fresh.setNarrativeSource(source);
        fresh.setNarrativeModel(model);
        fresh.setNarrativeFailureCategory(failure);
        fresh.setNarrativePromptVersion(version);
        fresh.setNarrativeGeneratedAt(LocalDateTime.now());
        return repository.save(fresh);
    }

    private static String loadPrompt() {
        try {
            return new String(new ClassPathResource(PROMPT_RESOURCE).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Prompt introuvable : " + PROMPT_RESOURCE, e);
        }
    }
}
