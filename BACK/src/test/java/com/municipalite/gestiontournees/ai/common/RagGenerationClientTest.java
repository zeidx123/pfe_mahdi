package com.municipalite.gestiontournees.ai.common;

import com.municipalite.gestiontournees.ai.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RagGenerationClientTest {
    private static final String PROMPT = "Réponds avec les passages fournis.";
    private static final String ANSWER = "{\"answer\":\"Une photo APRÈS est nécessaire.\"}";
    private static final Map<String, Object> SCHEMA = Map.of("type", "object");
    private final AiProperties properties = new AiProperties();
    private final GeminiClient gemini = mock(GeminiClient.class);
    private final GroqClient groq = mock(GroqClient.class);
    private final RagGenerationClient client = new RagGenerationClient(gemini, groq, properties);

    @Test
    void existingConfigurationKeepsGeminiIndependentOfTheCollectionProvider() {
        properties.setCollectionProvider(AiProperties.CollectionProvider.GROQ);
        properties.getGemini().setModelText("configured-flash");
        when(gemini.generateJson(PROMPT)).thenReturn(ANSWER);

        assertThat(client.generateJson(PROMPT, SCHEMA)).isEqualTo(ANSWER);
        assertThat(client.getProvider()).isEqualTo("GEMINI");
        assertThat(client.getModel()).isEqualTo("configured-flash");
        verify(gemini).generateJson(PROMPT);
        verifyNoMoreInteractions(gemini);
        verifyNoInteractions(groq);
    }

    @Test
    void groqUsesItsTextModelAndPassesTheGroundingSchemaWithoutChangingEmbeddings() {
        properties.getRag().setProvider(AiProperties.RagGenerationProvider.GROQ);
        properties.setCollectionProvider(AiProperties.CollectionProvider.OPENROUTER);
        properties.getGroq().setModelText("configured-qwen-text");
        properties.getGroq().setModelVision("separate-vision-model");
        properties.getRag().setEmbeddingModel("retained-embedding-model");
        when(groq.generateJson(PROMPT, SCHEMA)).thenReturn(ANSWER);

        assertThat(client.generateJson(PROMPT, SCHEMA)).isEqualTo(ANSWER);
        assertThat(client.getProvider()).isEqualTo("GROQ");
        assertThat(client.getModel()).isEqualTo("configured-qwen-text");
        assertThat(properties.getRag().getEmbeddingModel()).isEqualTo("retained-embedding-model");
        verify(groq).generateJson(PROMPT, SCHEMA);
        verifyNoMoreInteractions(groq);
        verifyNoInteractions(gemini);
    }

    @ParameterizedTest
    @EnumSource(AiProperties.RagGenerationProvider.class)
    void failureIsPreservedWithoutRetryOrCallingAnotherProvider(AiProperties.RagGenerationProvider provider) {
        properties.getRag().setProvider(provider);
        AiServiceException failure = new AiServiceException("Unavailable", "PROVIDER_HTTP_ERROR", 429, null);
        if (provider == AiProperties.RagGenerationProvider.GEMINI) {
            when(gemini.generateJson(PROMPT)).thenThrow(failure);
        } else {
            when(groq.generateJson(PROMPT, SCHEMA)).thenThrow(failure);
        }

        assertThatThrownBy(() -> client.generateJson(PROMPT, SCHEMA)).isSameAs(failure);
        if (provider == AiProperties.RagGenerationProvider.GEMINI) {
            verify(gemini).generateJson(PROMPT);
            verifyNoInteractions(groq);
        } else {
            verify(groq).generateJson(PROMPT, SCHEMA);
            verifyNoInteractions(gemini);
        }
        verifyNoMoreInteractions(gemini, groq);
    }
}
