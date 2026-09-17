package com.municipalite.gestiontournees.ai.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Service de bas niveau pour appeler l'API Google Gemini.
 * Utilisé par tous les agents IA (texte et vision).
 * Ce service ne doit JAMAIS être injecté directement dans un Controller.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiClient {

    private final OkHttpClient httpClient;
    private final AiProperties aiProperties;

    @Qualifier("aiObjectMapper")
    private final ObjectMapper objectMapper;

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    /**
     * Envoie un prompt texte à Gemini et retourne le texte de la réponse.
     */
    public String generateText(String prompt) {
        try {
            String model = aiProperties.getGemini().getModelText();
            String url = buildUrl(model);

            Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(Map.of("text", prompt)))
                )
            );

            String json = objectMapper.writeValueAsString(requestBody);
            String responseJson = postJson(url, json);
            return extractTextFromResponse(responseJson);

        } catch (Exception e) {
            log.error("Erreur appel Gemini (texte) : {}", e.getMessage());
            throw new AiServiceException("Le service IA est temporairement indisponible. Veuillez réessayer.", e);
        }
    }

    /**
     * Envoie une image (bytes) + un prompt texte à Gemini Vision et retourne le texte de la réponse.
     */
    public String analyzeImage(byte[] imageBytes, String mimeType, String prompt) {
        try {
            String model = aiProperties.getGemini().getModelVision();
            String url = buildUrl(model);
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(
                        Map.of("text", prompt),
                        Map.of("inline_data", Map.of(
                            "mime_type", mimeType,
                            "data", base64Image
                        ))
                    ))
                )
            );

            String json = objectMapper.writeValueAsString(requestBody);
            String responseJson = postJson(url, json);
            return extractTextFromResponse(responseJson);

        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur appel Gemini Vision : {}", e.getMessage());
            throw new AiServiceException("Le service d'analyse d'image est temporairement indisponible.", e);
        }
    }

    private String buildUrl(String model) {
        String baseUrl = aiProperties.getGemini().getBaseUrl();
        String apiKey = aiProperties.getGemini().getApiKey();
        return baseUrl + "/models/" + model + ":generateContent?key=" + apiKey;
    }

    private String postJson(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(json, JSON_TYPE);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "null";
                log.error("Gemini API error {}: {}", response.code(), errorBody);
                throw new AiServiceException("Erreur API Gemini (" + response.code() + "). Vérifiez votre clé API.");
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    private String extractTextFromResponse(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode candidates = root.path("candidates");
        if (candidates.isArray() && candidates.size() > 0) {
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (parts.isArray() && parts.size() > 0) {
                return parts.get(0).path("text").asText("");
            }
        }
        log.warn("Réponse Gemini inattendue : {}", responseJson);
        return "";
    }
}
