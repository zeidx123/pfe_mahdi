package com.municipalite.gestiontournees.ai.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.common.AiServiceException;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeminiEmbeddingServiceTest {
    @Test void missingKeyIsExplicitFailureAndNeverCreatesHashVector() {
        OkHttpClient http = mock(OkHttpClient.class);
        GeminiEmbeddingService service = new GeminiEmbeddingService(http, new AiProperties(), new ObjectMapper());
        assertThatThrownBy(() -> service.generateEmbedding("une procédure")) .isInstanceOf(AiServiceException.class);
        verifyNoInteractions(http);
    }
}
