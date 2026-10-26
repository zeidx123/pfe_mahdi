package com.municipalite.gestiontournees.ai.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Direct Groq adapter for the reviewed Qwen model; account quotas control free-tier usage. */
@Service
public class GroqClient {
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final List<String> STATUSES = List.of("CLEANED", "NOT_CLEANED", "OBSTRUCTED", "CANNOT_DETERMINE");
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private final OkHttpClient httpClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public GroqClient(OkHttpClient httpClient, AiProperties aiProperties,
                            @Qualifier("aiObjectMapper") ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        int timeout = Math.max(1, Math.min(120, aiProperties.getGroq().getTimeoutSeconds()));
        this.httpClient = httpClient.newBuilder()
                .callTimeout(timeout, TimeUnit.SECONDS)
                .connectTimeout(Math.min(timeout, 10), TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
    }

    public String generateJson(String prompt, Map<String, Object> schema) {
        if (prompt == null || prompt.isBlank()) {
            throw failure("La demande d'analyse ne peut pas être vide.", "INVALID_PROMPT", null);
        }
        if (schema == null || !"object".equals(schema.get("type"))) {
            throw failure("Le schéma de réponse IA doit décrire un objet JSON.", "INVALID_SCHEMA_CONFIGURATION", null);
        }
        return complete(aiProperties.getGroq().getModelText(), List.of(Map.of("type", "text", "text", prompt)),
                Map.of("type", "json_schema", "json_schema",
                        Map.of("name", "structured_response", "strict", true, "schema", schema)), 4096).text();
    }

    public String analyzeImages(List<VisionImageInput> images, String prompt) {
        if (prompt == null || prompt.isBlank() || images == null || images.isEmpty() || images.size() > 2
                || images.stream().anyMatch(image -> image == null || image.bytes() == null
                || image.bytes().length == 0 || image.mimeType() == null || !IMAGE_TYPES.contains(image.mimeType()))) {
            throw failure("Fournissez une photo APRÈS ou une paire AVANT/APRÈS valide.", "INVALID_IMAGE_INPUT", null);
        }
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt));
        for (VisionImageInput image : images) {
            content.add(Map.of("type", "image_url", "image_url", Map.of("url",
                    "data:" + image.mimeType() + ";base64," + Base64.getEncoder().encodeToString(image.bytes()))));
        }
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "status", Map.of("type", "string", "enum", STATUSES),
                        "description", Map.of("type", "string", "description", "Brève justification en français fondée sur les éléments visibles.")),
                "required", List.of("status", "description"),
                "additionalProperties", false);
        JsonResponse result = complete(aiProperties.getGroq().getModelVision(), content,
                Map.of("type", "json_schema", "json_schema",
                        Map.of("name", "point_cleanliness", "strict", true, "schema", schema)), 1024);
        JsonNode assessment = result.value();
        if (!STATUSES.contains(assessment.path("status").asText())
                || !assessment.path("description").isTextual() || assessment.path("description").asText().isBlank()) {
            throw failure("Le fournisseur IA a retourné une analyse invalide.", "INVALID_PROVIDER_RESPONSE", null);
        }
        return result.text();
    }

    private JsonResponse complete(String model, List<Map<String, Object>> content,
                                  Map<String, Object> responseFormat, int maxTokens) {
        AiProperties.Groq configuration = aiProperties.getGroq();
        String apiKey = configuration.getApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("VOTRE_CLE")) {
            throw failure("Le fournisseur IA n'est pas configuré.", "NOT_CONFIGURED", null);
        }
        if (!"qwen/qwen3.8-27b".equals(model)) {
            throw failure("Le modèle Groq pris en charge est qwen/qwen3.8-27b.",
                    "INVALID_MODEL_CONFIGURATION", null);
        }
        String baseUrl = configuration.getBaseUrl();
        if (baseUrl == null || !"https://api.groq.com/openai/v1".equals(baseUrl.replaceAll("/+$", ""))) {
            throw failure("L'adresse du fournisseur IA n'est pas configurée correctement.",
                    "INVALID_PROVIDER_CONFIGURATION", null);
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", content)),
                "stream", false,
                "temperature", 0,
                // Groq documents reasoning_effort=none as Qwen's non-thinking mode.
                "reasoning_effort", "none",
                "max_completion_tokens", maxTokens,
                "response_format", responseFormat);
        try {
            Request request = new Request.Builder()
                    .url(baseUrl.replaceAll("/+$", "") + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON_TYPE))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    if (response.code() == 400 && responseFormat != null && "json_schema".equals(responseFormat.get("type"))) {
                        // Fallback to json_object if Groq constrained grammar fails on strict json_schema
                        return complete(model, content, Map.of("type", "json_object"), maxTokens);
                    }
                    throw failure("Le fournisseur IA est indisponible (HTTP " + response.code() + ").",
                            "PROVIDER_HTTP_ERROR", response.code());
                }
                if (response.body() == null) {
                    throw failure("Le fournisseur IA n'a retourné aucune réponse exploitable.", "EMPTY_RESPONSE", null);
                }
                JsonNode root = objectMapper.readTree(response.body().string());
                if (root == null || !root.isObject()) {
                    throw failure("Le fournisseur IA a retourné une réponse illisible.", "INVALID_PROVIDER_RESPONSE", null);
                }
                if (root.hasNonNull("error")) {
                    throw failure("Le fournisseur IA a signalé une erreur.", "PROVIDER_RESPONSE_ERROR", response.code());
                }
                JsonNode choice = root.path("choices").path(0);
                String finishReason = choice.path("finish_reason").asText("");
                if ("length".equals(finishReason)) {
                    throw failure("La réponse du fournisseur IA est incomplète.", "RESPONSE_TRUNCATED", null);
                }
                if ("content_filter".equals(finishReason)) {
                    throw failure("Le fournisseur IA n'a pas pu analyser ce contenu.", "CONTENT_FILTER", null);
                }
                if ((!finishReason.isEmpty() && !"stop".equals(finishReason)) || choice.hasNonNull("error")) {
                    throw failure("Le fournisseur IA a retourné une réponse inexploitable.", "INVALID_PROVIDER_RESPONSE", null);
                }
                JsonNode message = choice.path("message");
                if (!message.path("refusal").asText("").isBlank()) {
                    throw failure("Le fournisseur IA n'a pas pu analyser ce contenu.", "PROVIDER_REFUSAL", null);
                }
                JsonNode result = message.path("content");
                String answer = result.isTextual() ? result.asText() : textParts(result);
                if (answer.isBlank()) {
                    throw failure("Le fournisseur IA n'a retourné aucune réponse exploitable.", "EMPTY_RESPONSE", null);
                }
                JsonNode value = objectMapper.readTree(answer);
                if (value == null || !value.isObject()) {
                    throw failure("Le fournisseur IA a retourné une analyse invalide.", "INVALID_PROVIDER_RESPONSE", null);
                }
                return new JsonResponse(answer, value);
            }
        } catch (AiServiceException exception) {
            throw exception;
        } catch (InterruptedIOException exception) {
            throw failure("Le délai de réponse du fournisseur IA est dépassé.", "PROVIDER_TIMEOUT", null);
        } catch (JsonProcessingException exception) {
            throw failure("Le fournisseur IA a retourné une réponse illisible.", "INVALID_PROVIDER_RESPONSE", null);
        } catch (IllegalArgumentException exception) {
            throw failure("La configuration du fournisseur IA est invalide.", "INVALID_PROVIDER_CONFIGURATION", null);
        } catch (Exception exception) {
            throw failure("Le fournisseur IA est temporairement indisponible.", "PROVIDER_UNAVAILABLE", null);
        }
    }

    private record JsonResponse(String text, JsonNode value) {}

    private String textParts(JsonNode content) {
        StringBuilder text = new StringBuilder();
        if (content.isArray()) {
            for (JsonNode part : content) {
                if ("text".equals(part.path("type").asText()) && part.path("text").isTextual()) {
                    text.append(part.path("text").asText());
                }
            }
        }
        return text.toString();
    }

    private AiServiceException failure(String message, String category, Integer httpStatus) {
        // Do not retain provider bodies, request URLs, API keys or transport exception details.
        return new AiServiceException(message, category, httpStatus, null);
    }
}
