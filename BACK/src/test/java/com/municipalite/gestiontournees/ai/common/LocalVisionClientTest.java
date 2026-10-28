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
import org.junit.jupiter.params.provider.ValueSource;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalVisionClientTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SHA = "da40e684d2ddc268ae3c976e668dcd2a9861a5e42c46d6143a1dea27b1bbef97";
    private static final VisionImageInput AFTER = new VisionImageInput(new byte[]{1, 2, 3}, "image/jpeg");

    @Test
    void sendsAfterBytesThresholdsAndTokenAndReturnsValidatedPrediction() throws Exception {
        AiProperties configuration = configuration();
        configuration.getLocalVision().setTimeoutSeconds(7);
        configuration.getLocalVision().setToken("fixture-service-token");
        configuration.getLocalVision().setMinSharpness(118.5);
        configuration.getLocalVision().setMinBrightness(30.0);
        AtomicReference<Request> sent = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        LocalVisionClient client = client(configuration, chain -> {
            sent.set(chain.request());
            Buffer buffer = new Buffer();
            chain.request().body().writeTo(buffer);
            body.set(buffer.readByteArray());
            assertThat(chain.call().timeout().timeoutNanos()).isEqualTo(TimeUnit.SECONDS.toNanos(7));
            return response(chain, 200, prediction(0.02, 0.07, 0.91, "PROPRE", null, SHA));
        });

        LocalVisionClient.Prediction result = client.predict(AFTER);

        assertThat(sent.get().url().toString()).isEqualTo("http://127.0.0.1:8090/predict");
        assertThat(sent.get().method()).isEqualTo("POST");
        assertThat(sent.get().body().contentType().toString()).startsWith("image/jpeg");
        assertThat(body.get()).containsExactly(1, 2, 3);
        assertThat(sent.get().header("X-Service-Token")).isEqualTo("fixture-service-token");
        assertThat(sent.get().header("X-Min-Sharpness")).isEqualTo("118.5");
        assertThat(sent.get().header("X-Min-Brightness")).isEqualTo("30.0");
        assertThat(sent.get().header("X-Max-Brightness")).isNull();
        assertThat(result.predictedClass()).isEqualTo("PROPRE");
        assertThat(result.probabilities()).containsExactly(java.util.Map.entry("CANNOT_DETERMINE", 0.02),
                java.util.Map.entry("DEBORDEMENT", 0.07), java.util.Map.entry("PROPRE", 0.91));
        assertThat(result.qualityIssue()).isNull();
        assertThat(result.modelSha256()).isEqualTo(SHA);
        assertThat(client.modelId()).isEqualTo("proprete_mobilenetv3l@sha256:" + SHA);
    }

    @Test
    void emptyTokenAndThresholdsAreNotSent() {
        AtomicReference<Request> sent = new AtomicReference<>();
        LocalVisionClient client = client(configuration(), chain -> {
            sent.set(chain.request());
            return response(chain, 200, prediction(0.9, 0.05, 0.05, "CANNOT_DETERMINE", "FLOUE", SHA));
        });
        assertThat(client.predict(AFTER).qualityIssue()).isEqualTo("FLOUE");
        assertThat(sent.get().header("X-Service-Token")).isNull();
        assertThat(sent.get().header("X-Min-Sharpness")).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 413, 415, 500, 503})
    void httpFailuresExposeOnlyStatus(int status) {
        AtomicInteger attempts = new AtomicInteger();
        AiProperties configuration = configuration();
        configuration.getLocalVision().setToken("fixture-service-token");
        LocalVisionClient client = client(configuration, chain -> {
            attempts.incrementAndGet();
            return response(chain, status, "{\"error\":\"X\",\"message\":\"private-service-detail\"}");
        });
        assertThatThrownBy(() -> client.predict(AFTER))
                .isInstanceOfSatisfying(AiServiceException.class, failure -> {
                    assertThat(failure.getCategory()).isEqualTo("PROVIDER_HTTP_ERROR");
                    assertThat(failure.getProviderHttpStatus()).isEqualTo(status);
                    assertThat(failure.getCause()).isNull();
                }).hasMessageNotContaining("private-service-detail")
                .hasMessageNotContaining("fixture-service-token");
        assertThat(attempts.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-json",
            "{}",
            "{\"classes\":[\"PROPRE\",\"DEBORDEMENT\",\"CANNOT_DETERMINE\"],\"probabilities\":{\"CANNOT_DETERMINE\":0.1,\"DEBORDEMENT\":0.1,\"PROPRE\":0.8},\"predicted_class\":\"PROPRE\",\"quality\":{\"sharpness\":1,\"mean_brightness\":1,\"issue\":null},\"model\":{\"sha256\":\"" + SHA + "\"}}",
            "{\"classes\":[\"CANNOT_DETERMINE\",\"DEBORDEMENT\",\"PROPRE\"],\"probabilities\":{\"CANNOT_DETERMINE\":0.5,\"DEBORDEMENT\":0.5,\"PROPRE\":0.5},\"predicted_class\":\"PROPRE\",\"quality\":{\"sharpness\":1,\"mean_brightness\":1,\"issue\":null},\"model\":{\"sha256\":\"" + SHA + "\"}}",
            "{\"classes\":[\"CANNOT_DETERMINE\",\"DEBORDEMENT\",\"PROPRE\"],\"probabilities\":{\"CANNOT_DETERMINE\":-0.1,\"DEBORDEMENT\":0.2,\"PROPRE\":0.9},\"predicted_class\":\"PROPRE\",\"quality\":{\"sharpness\":1,\"mean_brightness\":1,\"issue\":null},\"model\":{\"sha256\":\"" + SHA + "\"}}",
            "{\"classes\":[\"CANNOT_DETERMINE\",\"DEBORDEMENT\",\"PROPRE\"],\"probabilities\":{\"CANNOT_DETERMINE\":0.1,\"DEBORDEMENT\":0.1,\"PROPRE\":0.8},\"predicted_class\":\"DEBORDEMENT\",\"quality\":{\"sharpness\":1,\"mean_brightness\":1,\"issue\":null},\"model\":{\"sha256\":\"" + SHA + "\"}}",
            "{\"classes\":[\"CANNOT_DETERMINE\",\"DEBORDEMENT\",\"PROPRE\"],\"probabilities\":{\"CANNOT_DETERMINE\":0.1,\"DEBORDEMENT\":0.1,\"PROPRE\":0.8},\"predicted_class\":\"PROPRE\",\"quality\":{\"sharpness\":1,\"mean_brightness\":1,\"issue\":\"AUTRE\"},\"model\":{\"sha256\":\"" + SHA + "\"}}",
            "{\"classes\":[\"CANNOT_DETERMINE\",\"DEBORDEMENT\",\"PROPRE\"],\"probabilities\":{\"CANNOT_DETERMINE\":0.1,\"DEBORDEMENT\":0.1,\"PROPRE\":\"0.8\"},\"predicted_class\":\"PROPRE\",\"quality\":{\"sharpness\":1,\"mean_brightness\":1,\"issue\":null},\"model\":{\"sha256\":\"" + SHA + "\"}}"})
    void invalidAnswersAreRejected(String body) {
        LocalVisionClient client = client(configuration(), chain -> response(chain, 200, body));
        assertFailure(client, "INVALID_PROVIDER_RESPONSE");
    }

    @Test
    void otherModelIsRejected() {
        LocalVisionClient client = client(configuration(), chain -> response(chain, 200,
                prediction(0.1, 0.1, 0.8, "PROPRE", null, "0".repeat(64))));
        assertFailure(client, "MODEL_MISMATCH");
    }

    @Test
    void timeoutAndUnreachableServiceAreDistinct() {
        LocalVisionClient slow = client(configuration(), chain -> { throw new SocketTimeoutException("private-timeout-detail"); });
        assertFailure(slow, "PROVIDER_TIMEOUT");
        LocalVisionClient down = client(configuration(), chain -> { throw new ConnectException("private-connect-detail"); });
        assertFailure(down, "PROVIDER_UNAVAILABLE");
    }

    @Test
    void invalidInputOrConfigurationNeverSendsARequest() {
        AtomicInteger attempts = new AtomicInteger();
        AiProperties configuration = configuration();
        LocalVisionClient client = client(configuration, chain -> {
            attempts.incrementAndGet();
            return response(chain, 200, prediction(0.1, 0.1, 0.8, "PROPRE", null, SHA));
        });
        assertThatThrownBy(() -> client.predict(new VisionImageInput(new byte[]{1}, "image/webp")))
                .isInstanceOfSatisfying(AiServiceException.class, f -> assertThat(f.getCategory()).isEqualTo("INVALID_IMAGE_INPUT"));
        configuration.getLocalVision().setBaseUrl("not a url");
        assertFailure(client, "INVALID_PROVIDER_CONFIGURATION");
        assertThat(attempts.get()).isZero();
    }

    static String prediction(double c, double d, double p, String predicted, String issue, String sha) {
        return "{\"classes\":[\"CANNOT_DETERMINE\",\"DEBORDEMENT\",\"PROPRE\"],"
                + "\"probabilities\":{\"CANNOT_DETERMINE\":" + c + ",\"DEBORDEMENT\":" + d + ",\"PROPRE\":" + p + "},"
                + "\"predicted_class\":\"" + predicted + "\","
                + "\"quality\":{\"sharpness\":412.5,\"mean_brightness\":120.25,\"gate_enabled\":" + (issue != null)
                + ",\"issue\":" + (issue == null ? "null" : "\"" + issue + "\"") + "},"
                + "\"model\":{\"name\":\"proprete_mobilenetv3l\",\"sha256\":\"" + sha + "\"},"
                + "\"exif_orientation_applied\":false,\"latency_ms\":12.3}";
    }

    private static AiProperties configuration() {
        return new AiProperties();
    }

    private static LocalVisionClient client(AiProperties configuration, Interceptor interceptor) {
        return new LocalVisionClient(new OkHttpClient.Builder().addInterceptor(interceptor).build(), configuration, JSON);
    }

    private static Response response(Interceptor.Chain chain, int status, String body) {
        return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status)
                .message("fixture").body(ResponseBody.create(body, MediaType.get("application/json"))).build();
    }

    private static void assertFailure(LocalVisionClient client, String category) {
        assertThatThrownBy(() -> client.predict(AFTER))
                .isInstanceOfSatisfying(AiServiceException.class, failure -> {
                    assertThat(failure.getCategory()).isEqualTo(category);
                    assertThat(failure.getCause()).isNull();
                }).hasMessageNotContaining("private-")
                .hasMessageNotContaining("fixture-service-token");
    }
}
