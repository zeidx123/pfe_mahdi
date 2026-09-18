package com.municipalite.gestiontournees.ai.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.common.AiServiceException;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Real embeddings only. Missing provider availability must never manufacture vectors. */
@Service
@RequiredArgsConstructor
public class GeminiEmbeddingService implements EmbeddingService {
    private final OkHttpClient httpClient;
    private final AiProperties aiProperties;
    @Qualifier("aiObjectMapper")
    private final ObjectMapper objectMapper;
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    @Override
    public List<Float> generateEmbedding(String text) {
        String key = aiProperties.getGemini().getApiKey();
        if (key == null || key.isBlank() || key.contains("VOTRE_CLE")) throw new AiServiceException("Fournisseur d'embeddings non configuré.");
        String model = aiProperties.getRag().getEmbeddingModel();
        if (model == null || !model.matches("[A-Za-z0-9._-]+")) throw new AiServiceException("Modèle d'embeddings invalide.");
        int dimensions = aiProperties.getRag().getEmbeddingDimensions();
        try {
            Map<String, Object> payload = Map.of("model", "models/" + model,
                    "content", Map.of("parts", List.of(Map.of("text", text))), "outputDimensionality", dimensions);
            Request request = new Request.Builder()
                    .url(aiProperties.getGemini().getBaseUrl() + "/models/" + model + ":embedContent")
                    .header("x-goog-api-key", key)
                    .post(RequestBody.create(objectMapper.writeValueAsString(payload), JSON_TYPE)).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new AiServiceException("Fournisseur d'embeddings indisponible (HTTP " + response.code() + ").");
                }
                JsonNode values = objectMapper.readTree(response.body().string()).path("embedding").path("values");
                if (!values.isArray() || values.size() != dimensions) throw new AiServiceException("Dimensions d'embedding incompatibles.");
                List<Float> vector = new ArrayList<>(dimensions);
                double squaredNorm = 0;
                for (JsonNode value : values) {
                    if (!value.isNumber() || !Float.isFinite(value.floatValue())) throw new AiServiceException("Embedding invalide.");
                    float component = value.floatValue();
                    vector.add(component);
                    squaredNorm += component * (double) component;
                }
                if (squaredNorm == 0) throw new AiServiceException("Embedding vide.");
                return vector;
            }
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceException("Fournisseur d'embeddings temporairement indisponible.", e);
        }
    }

    @Override
    public List<List<Float>> generateEmbeddings(List<String> texts) {
        List<List<Float>> vectors = new ArrayList<>();
        for (String text : texts) vectors.add(generateEmbedding(text));
        return vectors;
    }
}
