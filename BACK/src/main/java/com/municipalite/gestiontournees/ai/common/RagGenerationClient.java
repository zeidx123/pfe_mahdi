package com.municipalite.gestiontournees.ai.common;

import com.municipalite.gestiontournees.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Explicit assistant generation provider; embeddings keep their independent configuration. */
@Service
@RequiredArgsConstructor
public class RagGenerationClient {
    private final GeminiClient gemini;
    private final GroqClient groq;
    private final AiProperties properties;

    public String generateJson(String prompt, Map<String, Object> schema) {
        return switch (properties.getRag().getProvider()) {
            case GEMINI -> gemini.generateJson(prompt);
            case GROQ -> groq.generateJson(prompt, schema);
        };
    }

    public String getProvider() {
        return properties.getRag().getProvider().name();
    }

    public String getModel() {
        return switch (properties.getRag().getProvider()) {
            case GEMINI -> properties.getGemini().getModelText();
            case GROQ -> properties.getGroq().getModelText();
        };
    }
}
