package com.municipalite.gestiontournees.ai.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Provider adapter. Failures are explicit; callers choose an honest degraded response. */
@Service
@RequiredArgsConstructor
public class GeminiClient {
    private final OkHttpClient httpClient;
    private final AiProperties aiProperties;
    @Qualifier("aiObjectMapper")
    private final ObjectMapper objectMapper;
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    public record ImageInput(byte[] bytes, String mimeType) {}

    public String generateText(String prompt) {
        return generate(aiProperties.getGemini().getModelText(), List.of(Map.of("text", prompt)), false);
    }

    public String generateJson(String prompt) {
        return generate(aiProperties.getGemini().getModelText(), List.of(Map.of("text", prompt)), true);
    }

    public String analyzeImage(byte[] imageBytes, String mimeType, String prompt) {
        return analyzeImages(List.of(new ImageInput(imageBytes, mimeType)), prompt);
    }

    public String analyzeImages(List<ImageInput> images, String prompt) {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt));
        for (ImageInput input : images) {
            parts.add(Map.of("inline_data", Map.of("mime_type", input.mimeType(),
                    "data", Base64.getEncoder().encodeToString(input.bytes()))));
        }
        return generate(aiProperties.getGemini().getModelVision(), parts, true);
    }

    private String generate(String model, List<Map<String, Object>> parts, boolean jsonOutput) {
        String apiKey = aiProperties.getGemini().getApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("VOTRE_CLE")) {
            throw new AiServiceException("Le fournisseur IA n'est pas configuré.");
        }
        if (model == null || !model.matches("[A-Za-z0-9._-]+")) {
            throw new AiServiceException("Le modèle IA n'est pas configuré correctement.");
        }
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("contents", List.of(Map.of("parts", parts)));
            if (jsonOutput) body.put("generationConfig", Map.of("responseMimeType", "application/json", "temperature", 0));
            Request request = new Request.Builder()
                    .url(aiProperties.getGemini().getBaseUrl() + "/models/" + model + ":generateContent")
                    .header("x-goog-api-key", apiKey)
                    .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON_TYPE)).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new AiServiceException("Le fournisseur IA est indisponible (HTTP " + response.code() + ").");
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode responseParts = root.path("candidates").path(0).path("content").path("parts");
                StringBuilder answer = new StringBuilder();
                for (JsonNode part : responseParts) {
                    if (!part.path("thought").asBoolean(false)) answer.append(part.path("text").asText(""));
                }
                if (answer.toString().isBlank()) throw new AiServiceException("Le fournisseur IA n'a retourné aucune réponse exploitable.");
                return answer.toString();
            }
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            // Do not log request URLs, image/CV content, API keys, or provider response bodies.
            throw new AiServiceException("Le fournisseur IA est temporairement indisponible.", e);
        }
    }
}
