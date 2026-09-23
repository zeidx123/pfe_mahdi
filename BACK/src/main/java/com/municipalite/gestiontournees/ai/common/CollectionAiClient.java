package com.municipalite.gestiontournees.ai.common;

import com.municipalite.gestiontournees.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Explicit provider selection for CVs and collection photos, independent of RAG. */
@Service
@RequiredArgsConstructor
public class CollectionAiClient {
    private final AiProperties properties;
    private final GroqClient groq;
    private final OpenRouterClient openRouter;

    public String generateJson(String prompt, Map<String, Object> schema) {
        return switch (properties.getCollectionProvider()) {
            case GROQ -> groq.generateJson(prompt, schema);
            case OPENROUTER -> openRouter.generateJson(prompt, schema);
        };
    }

    public String analyzeImages(List<VisionImageInput> images, String prompt) {
        return switch (properties.getCollectionProvider()) {
            case GROQ -> groq.analyzeImages(images, prompt);
            case OPENROUTER -> openRouter.analyzeImages(images, prompt);
        };
    }
}
