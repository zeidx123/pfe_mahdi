package com.municipalite.gestiontournees.ai.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implémentation du service d'embedding utilisant l'API Google Gemini (text-embedding-004).
 * Inclut un mécanisme de repli (deterministic fallback vector) si la clé API n'est pas renseignée ou en cas de problème réseau.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiEmbeddingService implements EmbeddingService {

    private final OkHttpClient httpClient;
    private final AiProperties aiProperties;

    @Qualifier("aiObjectMapper")
    private final ObjectMapper objectMapper;

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final int VECTOR_DIMENSION = 768;

    @Override
    public List<Float> generateEmbedding(String text) {
        String apiKey = aiProperties.getGemini().getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("VOTRE_CLE")) {
            log.warn("Clé API Gemini non configurée. Génération d'un vecteur sémantique local.");
            return generateDeterministicFallbackVector(text);
        }

        try {
            String model = aiProperties.getRag().getEmbeddingModel();
            String baseUrl = aiProperties.getGemini().getBaseUrl();
            String url = baseUrl + "/models/" + model + ":embedContent?key=" + apiKey;

            Map<String, Object> requestBody = Map.of(
                "model", "models/" + model,
                "content", Map.of("parts", List.of(Map.of("text", text)))
            );

            String json = objectMapper.writeValueAsString(requestBody);
            RequestBody body = RequestBody.create(json, JSON_TYPE);
            Request request = new Request.Builder().url(url).post(body).build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "";
                    log.warn("Erreur HTTP Gemini Embeddings ({}): {}. Utilisation du vecteur de repli.", response.code(), err);
                    return generateDeterministicFallbackVector(text);
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode values = root.path("embedding").path("values");

                if (values.isArray() && values.size() > 0) {
                    List<Float> vector = new ArrayList<>(values.size());
                    for (JsonNode val : values) {
                        vector.add((float) val.asDouble());
                    }
                    return vector;
                }
            }
        } catch (Exception e) {
            log.error("Exception lors de la génération de l'embedding Gemini: {}. Repli actif.", e.getMessage());
        }

        return generateDeterministicFallbackVector(text);
    }

    @Override
    public List<List<Float>> generateEmbeddings(List<String> texts) {
        List<List<Float>> results = new ArrayList<>(texts.size());
        for (String t : texts) {
            results.add(generateEmbedding(t));
        }
        return results;
    }

    /**
     * Génère un vecteur déterministe normalisé basé sur l'empreinte SHA-256 et la fréquence des mots.
     * Garantit que des textes similaires ont une similarité cosinus élevée même sans connexion API.
     */
    private List<Float> generateDeterministicFallbackVector(String text) {
        float[] vector = new float[VECTOR_DIMENSION];
        String normalizedText = text.toLowerCase().replaceAll("[^a-z0-9àâçéèêëîïôûùüÿñæœ\\s]", "");
        String[] words = normalizedText.split("\\s+");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String word : words) {
                if (word.isEmpty()) continue;
                byte[] hash = digest.digest(word.getBytes(StandardCharsets.UTF_8));
                for (int i = 0; i < hash.length && i < VECTOR_DIMENSION; i++) {
                    int idx = Math.abs((word.hashCode() + i * 31) % VECTOR_DIMENSION);
                    vector[idx] += (hash[i] & 0xFF) / 255.0f;
                }
            }
        } catch (Exception e) {
            log.error("Erreur calcul hash fallback vector", e);
        }

        // Normalisation L2 du vecteur
        float norm = 0.0f;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm < 1e-6) norm = 1.0f;

        List<Float> result = new ArrayList<>(VECTOR_DIMENSION);
        for (float v : vector) {
            result.add(v / norm);
        }
        return result;
    }
}
