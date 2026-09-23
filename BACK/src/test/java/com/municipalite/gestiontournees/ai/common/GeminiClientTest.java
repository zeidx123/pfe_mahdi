package com.municipalite.gestiontournees.ai.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import okhttp3.*;
import okio.Buffer;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class GeminiClientTest {
    @Test void structuredChatbotRequestUsesConfiguredModelAndHeaderWithoutNetwork() throws Exception {
        AiProperties properties = new AiProperties();
        properties.getGemini().setApiKey("fixture-key"); properties.getGemini().setModelText("fixture-model");
        AtomicReference<Request> requestRef = new AtomicReference<>();
        var http = new OkHttpClient.Builder().addInterceptor(chain -> {
            requestRef.set(chain.request());
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                    .body(ResponseBody.create("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"status\\\":\\\"CANNOT_DETERMINE\\\"}\"}]}}]}", MediaType.get("application/json"))).build();
        }).build();
        GeminiClient client = new GeminiClient(http, properties, new ObjectMapper());
        assertThat(client.generateJson("fixture")).contains("CANNOT_DETERMINE");
        Request sent = requestRef.get();
        assertThat(sent.url().toString()).contains("fixture-model:generateContent").doesNotContain("fixture-key");
        assertThat(sent.header("x-goog-api-key")).isEqualTo("fixture-key");
        Buffer body = new Buffer(); sent.body().writeTo(body);
        var json = new ObjectMapper().readTree(body.readUtf8());
        assertThat(json.path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(json.path("contents").get(0).path("parts").size()).isEqualTo(1);
    }

    @Test void httpFailureNeverExposesResponseBodyOrReturnsSuccessText() {
        AiProperties properties = new AiProperties(); properties.getGemini().setApiKey("fixture-key");
        var http = new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("Unavailable")
                .body(ResponseBody.create("private-provider-detail", MediaType.get("text/plain"))).build()).build();
        assertThatThrownBy(() -> new GeminiClient(http, properties, new ObjectMapper()).generateText("test"))
                .isInstanceOfSatisfying(AiServiceException.class, failure -> {
                    assertThat(failure.getCategory()).isEqualTo("PROVIDER_HTTP_ERROR");
                    assertThat(failure.getProviderHttpStatus()).isEqualTo(503);
                }).hasMessageContaining("503").hasMessageNotContaining("private-provider-detail");
    }

    @Test void timeoutAndInvalidProviderEnvelopeHaveDifferentSafeCategories() {
        AiProperties properties = new AiProperties(); properties.getGemini().setApiKey("fixture-key");
        var timeout = new OkHttpClient.Builder().addInterceptor(chain -> { throw new java.net.SocketTimeoutException("private-network-detail"); }).build();
        assertThatThrownBy(() -> new GeminiClient(timeout, properties, new ObjectMapper()).generateJson("fixture"))
                .isInstanceOfSatisfying(AiServiceException.class, failure -> assertThat(failure.getCategory()).isEqualTo("PROVIDER_TIMEOUT"))
                .hasMessageNotContaining("private-network-detail");
        var invalid = new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body(ResponseBody.create("{private-invalid-json", MediaType.get("application/json"))).build()).build();
        assertThatThrownBy(() -> new GeminiClient(invalid, properties, new ObjectMapper()).generateJson("fixture"))
                .isInstanceOfSatisfying(AiServiceException.class, failure -> assertThat(failure.getCategory()).isEqualTo("INVALID_PROVIDER_RESPONSE"))
                .hasMessageNotContaining("private-invalid-json");
    }
}
