package com.municipalite.gestiontournees.ai.vision.narrative;

import com.municipalite.gestiontournees.ai.common.CollectionAiClient;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Reuses the existing structured text path (CollectionAiClient.generateJson, as for CV analysis): same provider,
 * text model, quota and strict JSON schema. No new provider; the clients are not modified.
 */
@Component
@RequiredArgsConstructor
public class CollectionAiNarrativeGenerator implements NarrativeGenerator {
    static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("texte", Map.of("type", "string",
                    "description", "Résumé de 2 à 4 phrases en français, 600 caractères au maximum.")),
            "required", List.of("texte"),
            "additionalProperties", false);
    private final CollectionAiClient collectionAiClient;
    private final AiProperties aiProperties;

    @Override
    public String generate(String prompt) {
        return collectionAiClient.generateJson(prompt, SCHEMA);
    }

    @Override
    public String modelId() {
        return aiProperties.getCollectionProvider().name() + ":" + aiProperties.getCollectionTextModel();
    }
}
