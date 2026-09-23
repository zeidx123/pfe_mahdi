package com.municipalite.gestiontournees.ai.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroqClientTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ASSESSMENT = "{\"status\":\"CANNOT_DETERMINE\",\"description\":\"Le sol est hors champ.\"}";
    private static final List<VisionImageInput> AFTER = List.of(new VisionImageInput(new byte[]{2}, "image/png"));
    private static final Map<String, Object> CV_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("permis", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "value", Map.of("type", List.of("string", "null")),
                            "snippet", Map.of("type", List.of("string", "null"))),
                    "required", List.of("value", "snippet"),
                    "additionalProperties", false)),
            "required", List.of("permis"),
            "additionalProperties", false);

    @Test
    void sendsOrderedPrivateImagesAndStrictSchemaToGroq() throws Exception {
        AiProperties configuration = configuration();
        configuration.getGroq().setBaseUrl("https://api.groq.com/openai/v1/");
        configuration.getGroq().setTimeoutSeconds(7);
        configuration.getGroq().setModelText("unsupported-text-fixture");
        AtomicReference<Request> sent = new AtomicReference<>();
        GroqClient client = client(configuration, chain -> {
            sent.set(chain.request());
            assertThat(chain.call().timeout().timeoutNanos()).isEqualTo(TimeUnit.SECONDS.toNanos(7));
            assertThat(chain.connectTimeoutMillis()).isEqualTo(7000);
            assertThat(chain.readTimeoutMillis()).isEqualTo(7000);
            assertThat(chain.writeTimeoutMillis()).isEqualTo(7000);
            return response(chain, 200, envelope(ASSESSMENT, "stop"));
        });

        assertThat(client.analyzeImages(List.of(new VisionImageInput(new byte[]{1}, "image/jpeg"),
                new VisionImageInput(new byte[]{2}, "image/png")), "AVANT puis APRÈS")).isEqualTo(ASSESSMENT);

        Request request = sent.get();
        assertThat(request.url().toString()).isEqualTo("https://api.groq.com/openai/v1/chat/completions");
        assertThat(request.header("Authorization")).isEqualTo("Bearer fixture-private-key");
        assertThat(request.header("Accept")).isEqualTo("application/json");
        assertThat(request.body().contentType().toString()).startsWith("application/json");
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        var body = JSON.readTree(buffer.readUtf8());
        assertThat(body.path("model").asText()).isEqualTo("qwen/qwen3.8-27b");
        assertThat(body.has("models")).isFalse();
        assertThat(body.has("route")).isFalse();
        assertThat(body.path("stream").asBoolean()).isFalse();
        assertThat(body.path("temperature").asInt()).isZero();
        assertThat(body.path("reasoning_effort").asText()).isEqualTo("none");
        assertThat(body.path("max_completion_tokens").asInt()).isEqualTo(1024);
        var content = body.path("messages").path(0).path("content");
        assertThat(content.size()).isEqualTo(3);
        assertThat(content.path(0).path("text").asText()).isEqualTo("AVANT puis APRÈS");
        assertThat(content.path(1).path("image_url").path("url").asText()).isEqualTo("data:image/jpeg;base64,AQ==");
        assertThat(content.path(2).path("image_url").path("url").asText()).isEqualTo("data:image/png;base64,Ag==");
        assertThat(body.has("provider")).isFalse();
        assertThat(body.has("reasoning")).isFalse();
        assertThat(body.has("max_tokens")).isFalse();
        var format = body.path("response_format");
        assertThat(format.path("type").asText()).isEqualTo("json_schema");
        assertThat(format.path("json_schema").path("strict").asBoolean()).isTrue();
        var schema = format.path("json_schema").path("schema");
        assertThat(schema.path("properties").path("status").path("enum").toString())
                .contains("CLEANED", "NOT_CLEANED", "OBSTRUCTED", "CANNOT_DETERMINE")
                .doesNotContain("ANALYSIS_UNAVAILABLE");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("required").toString()).contains("status", "description");
    }

    @Test
    void cvExtractionUsesItsOwnTextModelAndStrictSchemaWithoutImageParts() throws Exception {
        AiProperties configuration = configuration();
        configuration.getGroq().setModelText("qwen/qwen3.8-27b");
        configuration.getGroq().setModelVision("unsupported-vision-fixture");
        String cv = "{\"permis\":{\"value\":\"C\",\"snippet\":\"Permis C\"}}";
        AtomicReference<Request> sent = new AtomicReference<>();
        GroqClient client = client(configuration, chain -> {
            sent.set(chain.request());
            return response(chain, 200, envelope(cv, "stop"));
        });

        assertThat(client.generateJson("Extraire ce CV en JSON, sans inventer les champs manquants.", CV_SCHEMA))
                .isEqualTo(cv);

        Buffer buffer = new Buffer();
        sent.get().body().writeTo(buffer);
        String serialized = buffer.readUtf8();
        var body = JSON.readTree(serialized);
        assertThat(body.path("model").asText()).isEqualTo("qwen/qwen3.8-27b");
        assertThat(body.path("reasoning_effort").asText()).isEqualTo("none");
        assertThat(body.path("max_completion_tokens").asInt()).isEqualTo(4096);
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_schema");
        var format = body.path("response_format").path("json_schema");
        assertThat(format.path("name").asText()).isEqualTo("structured_response");
        assertThat(format.path("strict").asBoolean()).isTrue();
        assertThat(format.path("schema")).isEqualTo(JSON.valueToTree(CV_SCHEMA));
        var content = body.path("messages").path(0).path("content");
        assertThat(content.size()).isEqualTo(1);
        assertThat(content.path(0).path("type").asText()).isEqualTo("text");
        assertThat(content.path(0).path("text").asText()).contains("sans inventer");
        assertThat(serialized).doesNotContain("image_url", "data:image", "vision-fixture");
        assertThat(body.has("provider")).isFalse();
        assertThat(body.has("reasoning")).isFalse();
        assertThat(body.has("max_tokens")).isFalse();
        assertThat(body.has("models")).isFalse();
        assertThat(body.has("route")).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"qwen/qwen3.8-27b:free", "openrouter/auto", "another/model"})
    void rejectsUnreviewedTextModelsBeforeNetwork(String model) {
        AiProperties configuration = configuration();
        configuration.getGroq().setModelText(model);
        AtomicInteger attempts = new AtomicInteger();
        GroqClient client = countingClient(configuration, attempts);
        assertFailure(() -> client.generateJson("Extraire ce CV en JSON.", CV_SCHEMA), "INVALID_MODEL_CONFIGURATION");
        assertThat(attempts.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 429, 503})
    void textProviderFailureIsExplicitAndNeverUsesFallback(int status) {
        AtomicInteger attempts = new AtomicInteger();
        GroqClient client = client(configuration(), chain -> {
            attempts.incrementAndGet();
            return response(chain, status, "{\"error\":{\"message\":\"private-provider-detail\"}}");
        });
        assertFailure(() -> client.generateJson("Extraire ce CV en JSON.", CV_SCHEMA),
                status == 200 ? "PROVIDER_RESPONSE_ERROR" : "PROVIDER_HTTP_ERROR");
        assertThat(attempts.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "private-invalid-json"})
    void textExtractionRequiresAJsonObject(String value) {
        GroqClient client = client(configuration(), chain -> response(chain, 200, envelope(value, "stop")));
        assertFailure(() -> client.generateJson("Extraire ce CV en JSON.", CV_SCHEMA), "INVALID_PROVIDER_RESPONSE");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "VOTRE_CLE_GROQ_ICI"})
    void unconfiguredKeyNeverSendsARequest(String key) {
        AiProperties configuration = configuration();
        configuration.getGroq().setApiKey(key);
        AtomicInteger attempts = new AtomicInteger();
        GroqClient client = countingClient(configuration, attempts);
        assertFailure(client, "NOT_CONFIGURED");
        assertFailure(() -> client.generateJson("Extraire ce CV en JSON.", CV_SCHEMA), "NOT_CONFIGURED");
        assertThat(attempts.get()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"qwen/qwen3.8-27b:free", "openrouter/auto", "openrouter/free", "another/model"})
    void rejectsUnreviewedVisionModelsBeforeNetwork(String model) {
        AiProperties configuration = configuration();
        configuration.getGroq().setModelVision(model);
        AtomicInteger attempts = new AtomicInteger();
        GroqClient client = countingClient(configuration, attempts);
        assertFailure(client, "INVALID_MODEL_CONFIGURATION");
        assertThat(attempts.get()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"http://api.groq.com/openai/v1", "https://api.groq.com.evil.invalid/openai/v1",
            "https://example.test", "https://api.groq.com/openai/v1?secret=fixture"})
    void refusesToSendCredentialsOutsideTheOfficialGroqEndpoint(String baseUrl) {
        AiProperties configuration = configuration();
        configuration.getGroq().setBaseUrl(baseUrl);
        AtomicInteger attempts = new AtomicInteger();
        GroqClient client = countingClient(configuration, attempts);
        assertFailure(client, "INVALID_PROVIDER_CONFIGURATION");
        assertFailure(() -> client.generateJson("Extraire ce CV en JSON.", CV_SCHEMA), "INVALID_PROVIDER_CONFIGURATION");
        assertThat(attempts.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 401, 429, 503})
    void httpFailuresExposeOnlyStatusAndNeverReturnSuccess(int status) {
        AtomicInteger attempts = new AtomicInteger();
        GroqClient client = client(configuration(), chain -> {
            attempts.incrementAndGet();
            return response(chain, status, "private-provider-detail fixture-private-key https://private.invalid");
        });
        assertThatThrownBy(() -> client.analyzeImages(AFTER, "fixture"))
                .isInstanceOfSatisfying(AiServiceException.class, failure -> {
                    assertThat(failure.getCategory()).isEqualTo("PROVIDER_HTTP_ERROR");
                    assertThat(failure.getProviderHttpStatus()).isEqualTo(status);
                    assertThat(failure.getCause()).isNull();
                }).hasMessageContaining(String.valueOf(status))
                .hasMessageNotContaining("private-provider-detail")
                .hasMessageNotContaining("fixture-private-key")
                .hasMessageNotContaining("private.invalid");
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void errorEnvelopeWithHttp200IsStillAFailure() {
        GroqClient client = client(configuration(), chain -> response(chain, 200,
                "{\"error\":{\"code\":429,\"message\":\"private-provider-detail\"}}"));
        assertFailure(client, "PROVIDER_RESPONSE_ERROR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{private-invalid-json", "null", "[]"})
    void rejectsInvalidProviderEnvelope(String body) {
        assertFailure(client(configuration(), chain -> response(chain, 200, body)), "INVALID_PROVIDER_RESPONSE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"choices\":[]}", "{\"choices\":[{\"message\":{\"content\":\"\"}}]}"})
    void rejectsEmptyResponses(String body) {
        assertFailure(client(configuration(), chain -> response(chain, 200, body)), "EMPTY_RESPONSE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{\"status\":\"NORMAL\",\"description\":\"Propre\"}",
            "{\"status\":\"CLEANED\"}", "{\"status\":\"CLEANED\",\"description\":\"\"}"})
    void rejectsUnusableAssessmentEvenWhenEnvelopeIsValid(String assessment) {
        assertFailure(client(configuration(), chain -> response(chain, 200, envelope(assessment, "stop"))),
                "INVALID_PROVIDER_RESPONSE");
    }

    @Test
    void truncatedFilteredAndRefusedOutputsCannotBecomeCleanedAssessments() {
        assertFailure(client(configuration(), chain -> response(chain, 200, envelope(ASSESSMENT, "length"))), "RESPONSE_TRUNCATED");
        GroqClient truncatedCv = client(configuration(), chain -> response(chain, 200,
                envelope("{\"permis\":", "length")));
        assertFailure(() -> truncatedCv.generateJson("Extraire ce CV en JSON.", CV_SCHEMA), "RESPONSE_TRUNCATED");
        assertFailure(client(configuration(), chain -> response(chain, 200, envelope(ASSESSMENT, "content_filter"))), "CONTENT_FILTER");
        assertFailure(client(configuration(), chain -> response(chain, 200,
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"refusal\":\"private-detail\"}}]}")), "PROVIDER_REFUSAL");
    }

    @Test
    void timeoutIsDistinctAndDoesNotExposeTransportDetails() {
        AtomicInteger attempts = new AtomicInteger();
        GroqClient client = client(configuration(), chain -> {
            attempts.incrementAndGet();
            throw new SocketTimeoutException("private-provider-detail fixture-private-key https://private.invalid");
        });
        assertFailure(client, "PROVIDER_TIMEOUT");
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void supportsTextPartsWithoutReturningReasoningParts() throws Exception {
        String body = JSON.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", "stop", "message",
                Map.of("content", List.of(Map.of("type", "reasoning", "text", "private-reasoning"),
                        Map.of("type", "text", "text", ASSESSMENT)))))));
        assertThat(client(configuration(), chain -> response(chain, 200, body)).analyzeImages(AFTER, "fixture"))
                .isEqualTo(ASSESSMENT);
    }

    @Test
    void missingImageTypeIsAnExplicitInputFailureWithoutNetwork() {
        AtomicInteger attempts = new AtomicInteger();
        GroqClient client = countingClient(configuration(), attempts);
        assertThatThrownBy(() -> client.analyzeImages(List.of(new VisionImageInput(new byte[]{1}, null)), "fixture"))
                .isInstanceOfSatisfying(AiServiceException.class,
                        failure -> assertThat(failure.getCategory()).isEqualTo("INVALID_IMAGE_INPUT"));
        assertThat(attempts.get()).isZero();
    }

    private static AiProperties configuration() {
        AiProperties configuration = new AiProperties();
        configuration.getGroq().setApiKey("fixture-private-key");
        configuration.getGroq().setModelVision("qwen/qwen3.8-27b");
        configuration.getGroq().setModelText("qwen/qwen3.8-27b");
        return configuration;
    }

    private static GroqClient client(AiProperties configuration, Interceptor interceptor) {
        return new GroqClient(new OkHttpClient.Builder().addInterceptor(interceptor).build(), configuration, JSON);
    }

    private static GroqClient countingClient(AiProperties configuration, AtomicInteger attempts) {
        return client(configuration, chain -> {
            attempts.incrementAndGet();
            return response(chain, 200, envelope(ASSESSMENT, "stop"));
        });
    }

    private static Response response(Interceptor.Chain chain, int status, String body) {
        return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status)
                .message("fixture").body(ResponseBody.create(body, MediaType.get("application/json"))).build();
    }

    private static String envelope(String assessment, String finishReason) throws java.io.IOException {
        return JSON.writeValueAsString(Map.of("choices", List.of(Map.of("finish_reason", finishReason,
                "message", Map.of("content", assessment)))));
    }

    private static void assertFailure(GroqClient client, String category) {
        assertFailure(() -> client.analyzeImages(AFTER, "fixture"), category);
    }

    private static void assertFailure(Runnable action, String category) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(AiServiceException.class, failure -> {
                    assertThat(failure.getCategory()).isEqualTo(category);
                    assertThat(failure.getCause()).isNull();
                }).hasMessageNotContaining("private-provider-detail")
                .hasMessageNotContaining("private-invalid-json")
                .hasMessageNotContaining("fixture-private-key")
                .hasMessageNotContaining("private.invalid");
    }
}
