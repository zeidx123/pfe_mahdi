package com.municipalite.gestiontournees.ai.common;

import com.municipalite.gestiontournees.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollectionAiClientTest {
    private final AiProperties properties = new AiProperties();
    private final GroqClient groq = mock(GroqClient.class);
    private final OpenRouterClient openRouter = mock(OpenRouterClient.class);
    private final CollectionAiClient client = new CollectionAiClient(properties, groq, openRouter);

    @Test void selectedGroqHandlesBothCollectionWorkflows() {
        properties.setCollectionProvider(AiProperties.CollectionProvider.GROQ);
        var images = List.of(new VisionImageInput(new byte[]{1}, "image/png"));
        var schema = Map.<String, Object>of("type", "object");
        when(groq.analyzeImages(images, "photo")).thenReturn("assessment");
        when(groq.generateJson("cv", schema)).thenReturn("extraction");
        assertThat(client.analyzeImages(images, "photo")).isEqualTo("assessment");
        assertThat(client.generateJson("cv", schema)).isEqualTo("extraction");
        verifyNoInteractions(openRouter);
    }

    @Test void explicitOpenRouterSelectionDoesNotCallGroq() {
        properties.setCollectionProvider(AiProperties.CollectionProvider.OPENROUTER);
        var images = List.of(new VisionImageInput(new byte[]{1}, "image/png"));
        var schema = Map.<String, Object>of("type", "object");
        when(openRouter.analyzeImages(images, "photo")).thenReturn("assessment");
        when(openRouter.generateJson("cv", schema)).thenReturn("extraction");
        assertThat(client.analyzeImages(images, "photo")).isEqualTo("assessment");
        assertThat(client.generateJson("cv", schema)).isEqualTo("extraction");
        verifyNoInteractions(groq);
    }

    @Test void providerFailureNeverTriggersAnotherProvider() {
        properties.setCollectionProvider(AiProperties.CollectionProvider.GROQ);
        var images = List.of(new VisionImageInput(new byte[]{1}, "image/png"));
        var failure = new AiServiceException("Unavailable", "PROVIDER_HTTP_ERROR", 429, null);
        when(groq.analyzeImages(images, "photo")).thenThrow(failure);
        assertThatThrownBy(() -> client.analyzeImages(images, "photo")).isSameAs(failure);
        verifyNoInteractions(openRouter);
    }
}
