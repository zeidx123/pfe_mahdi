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
    @Test void structuredPairedRequestUsesConfiguredModelAndHeaderWithoutNetwork() throws Exception {
        AiProperties properties = new AiProperties();
        properties.getGemini().setApiKey("fixture-key"); properties.getGemini().setModelVision("fixture-model");
        AtomicReference<Request> requestRef = new AtomicReference<>();
        var http = new OkHttpClient.Builder().addInterceptor(chain -> {
            requestRef.set(chain.request());
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                    .body(ResponseBody.create("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"status\\\":\\\"CANNOT_DETERMINE\\\"}\"}]}}]}", MediaType.get("application/json"))).build();
        }).build();
        GeminiClient client = new GeminiClient(http, properties, new ObjectMapper());
        assertThat(client.analyzeImages(List.of(new GeminiClient.ImageInput(new byte[]{1}, "image/png"), new GeminiClient.ImageInput(new byte[]{2}, "image/png")), "fixture")).contains("CANNOT_DETERMINE");
        Request sent = requestRef.get();
        assertThat(sent.url().toString()).contains("fixture-model:generateContent").doesNotContain("fixture-key");
        assertThat(sent.header("x-goog-api-key")).isEqualTo("fixture-key");
        Buffer body = new Buffer(); sent.body().writeTo(body);
        var json = new ObjectMapper().readTree(body.readUtf8());
        assertThat(json.path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(json.path("contents").get(0).path("parts").size()).isEqualTo(3);
    }

    @Test void httpFailureNeverExposesResponseBodyOrReturnsSuccessText() {
        AiProperties properties = new AiProperties(); properties.getGemini().setApiKey("fixture-key");
        var http = new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("Unavailable")
                .body(ResponseBody.create("private-provider-detail", MediaType.get("text/plain"))).build()).build();
        assertThatThrownBy(() -> new GeminiClient(http, properties, new ObjectMapper()).generateText("test"))
                .isInstanceOf(AiServiceException.class).hasMessageContaining("503").hasMessageNotContaining("private-provider-detail");
    }
}
