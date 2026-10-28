package com.municipalite.gestiontournees.ai.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Local adapter for the proprete_mobilenetv3l service (computer_vision/service, ONNX Runtime).
 * The model only knows CANNOT_DETERMINE, DEBORDEMENT and PROPRE; its labels are partly extrapolated,
 * CANNOT_DETERMINE was learnt on synthetic images and nothing is validated in the field.
 */
@Service
public class LocalVisionClient {
    public static final List<String> CLASSES = List.of("CANNOT_DETERMINE", "DEBORDEMENT", "PROPRE");
    public static final Set<String> QUALITY_ISSUES = Set.of("TROP_SOMBRE", "SUREXPOSEE", "FLOUE");
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");
    private final OkHttpClient httpClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    /** Validated service answer; probabilities keep the model output order. */
    public record Prediction(Map<String, Double> probabilities, String predictedClass, double sharpness,
                             double meanBrightness, String qualityIssue, String modelName, String modelSha256) {}

    public LocalVisionClient(OkHttpClient httpClient, AiProperties aiProperties,
                             @Qualifier("aiObjectMapper") ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        int timeout = Math.max(1, Math.min(120, aiProperties.getLocalVision().getTimeoutSeconds()));
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

    public String modelId() {
        AiProperties.LocalVision configuration = aiProperties.getLocalVision();
        return configuration.getModelName() + "@sha256:" + configuration.getModelSha256();
    }

    public Prediction predict(VisionImageInput image) {
        if (image == null || image.bytes() == null || image.bytes().length == 0
                || image.mimeType() == null || !IMAGE_TYPES.contains(image.mimeType())) {
            throw failure("Fournissez une photo APRÈS JPEG ou PNG valide.", "INVALID_IMAGE_INPUT", null);
        }
        AiProperties.LocalVision configuration = aiProperties.getLocalVision();
        HttpUrl base = configuration.getBaseUrl() == null ? null : HttpUrl.parse(configuration.getBaseUrl().trim());
        if (base == null || configuration.getModelSha256() == null || configuration.getModelSha256().isBlank()) {
            throw failure("Le moteur de vision local n'est pas configuré correctement.", "INVALID_PROVIDER_CONFIGURATION", null);
        }
        Request.Builder request = new Request.Builder()
                .url(base.newBuilder().addPathSegment("predict").build())
                .header("Accept", "application/json")
                .post(RequestBody.create(image.bytes(), MediaType.get(image.mimeType())));
        threshold(request, "X-Min-Sharpness", configuration.getMinSharpness());
        threshold(request, "X-Min-Brightness", configuration.getMinBrightness());
        threshold(request, "X-Max-Brightness", configuration.getMaxBrightness());
        if (configuration.getToken() != null && !configuration.getToken().isBlank()) {
            request.header("X-Service-Token", configuration.getToken());
        }
        try (Response response = httpClient.newCall(request.build()).execute()) {
            if (!response.isSuccessful()) {
                throw failure("Le service de vision local est indisponible (HTTP " + response.code() + ").",
                        "PROVIDER_HTTP_ERROR", response.code());
            }
            if (response.body() == null) {
                throw failure("Le service de vision local n'a retourné aucune réponse exploitable.", "EMPTY_RESPONSE", null);
            }
            return validated(objectMapper.readTree(response.body().string()), configuration.getModelSha256());
        } catch (AiServiceException exception) {
            throw exception;
        } catch (InterruptedIOException exception) {
            throw failure("Le délai de réponse du service de vision local est dépassé.", "PROVIDER_TIMEOUT", null);
        } catch (JsonProcessingException exception) {
            throw failure("Le service de vision local a retourné une réponse illisible.", "INVALID_PROVIDER_RESPONSE", null);
        } catch (IllegalArgumentException exception) {
            throw failure("La configuration du service de vision local est invalide.", "INVALID_PROVIDER_CONFIGURATION", null);
        } catch (IOException exception) {
            throw failure("Le service de vision local est injoignable.", "PROVIDER_UNAVAILABLE", null);
        } catch (Exception exception) {
            throw failure("Le service de vision local est temporairement indisponible.", "PROVIDER_UNAVAILABLE", null);
        }
    }

    private Prediction validated(JsonNode root, String expectedSha256) {
        if (root == null || !root.isObject() || !root.path("classes").isArray() || root.path("classes").size() != CLASSES.size()) {
            throw invalid();
        }
        for (int i = 0; i < CLASSES.size(); i++) {
            if (!CLASSES.get(i).equals(root.path("classes").path(i).asText())) throw invalid();
        }
        Map<String, Double> probabilities = new LinkedHashMap<>();
        double sum = 0;
        String best = null;
        for (String label : CLASSES) {
            JsonNode value = root.path("probabilities").path(label);
            if (!value.isNumber() || !Double.isFinite(value.asDouble()) || value.asDouble() < 0 || value.asDouble() > 1) {
                throw invalid();
            }
            probabilities.put(label, value.asDouble());
            sum += value.asDouble();
            if (best == null || value.asDouble() > probabilities.get(best)) best = label;
        }
        if (Math.abs(sum - 1.0) > 1e-3 || !best.equals(root.path("predicted_class").asText())) throw invalid();
        JsonNode quality = root.path("quality");
        JsonNode issue = quality.path("issue");
        if (!quality.path("sharpness").isNumber() || !Double.isFinite(quality.path("sharpness").asDouble())
                || !quality.path("mean_brightness").isNumber() || !Double.isFinite(quality.path("mean_brightness").asDouble())
                || !(issue.isNull() || issue.isMissingNode() || (issue.isTextual() && QUALITY_ISSUES.contains(issue.asText())))) {
            throw invalid();
        }
        String sha256 = root.path("model").path("sha256").asText("");
        if (!sha256.equalsIgnoreCase(expectedSha256)) {
            throw failure("Le service de vision local utilise un autre modèle que celui configuré.", "MODEL_MISMATCH", null);
        }
        return new Prediction(Collections.unmodifiableMap(probabilities), best, quality.path("sharpness").asDouble(),
                quality.path("mean_brightness").asDouble(), issue.isTextual() ? issue.asText() : null,
                root.path("model").path("name").asText(""), sha256.toLowerCase());
    }

    private static void threshold(Request.Builder request, String header, Double value) {
        if (value != null && Double.isFinite(value)) request.header(header, Double.toString(value));
    }

    private AiServiceException invalid() {
        return failure("Le service de vision local a retourné une analyse invalide.", "INVALID_PROVIDER_RESPONSE", null);
    }

    private AiServiceException failure(String message, String category, Integer httpStatus) {
        // Do not retain service bodies, tokens, URLs or transport exception details.
        return new AiServiceException(message, category, httpStatus, null);
    }
}
