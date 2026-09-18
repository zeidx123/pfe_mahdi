package com.municipalite.gestiontournees.ai.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.common.*;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.rag.dto.*;
import com.municipalite.gestiontournees.ai.rag.model.*;
import com.municipalite.gestiontournees.ai.rag.repository.RagConversationRepository;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagServiceTest {
    EmbeddingService embeddings = mock(EmbeddingService.class);
    VectorStoreService store = mock(VectorStoreService.class);
    GeminiClient gemini = mock(GeminiClient.class);
    RagConversationRepository histories = mock(RagConversationRepository.class);
    RagService service = new RagService(embeddings, store, gemini, histories, new AiProperties(), new ObjectMapper());
    DocumentChunk chunk = DocumentChunk.builder().id("source-1").documentId("doc-1").documentName("Procédure").pageNumber(2)
            .sectionTitle("Accès").content("Si le point est inaccessible, consulter le superviseur.").build();

    void retrievedSource() {
        when(store.searchSimilar(anyString(), any(), anyInt(), anyDouble())).thenReturn(List.of(new VectorStoreService.ScoredChunk(chunk, .8)));
    }

    @Test void embeddingFailureReturnsLiteralExcerptsWithoutInventingProcedure() {
        when(embeddings.generateEmbedding(anyString())).thenThrow(new AiServiceException("Unavailable"));
        retrievedSource();
        RagResponse response = service.query(new RagQueryRequest(null, "Point inaccessible ?"), "driver");
        assertThat(response.getStatus()).isEqualTo("SOURCE_EXCERPTS");
        assertThat(response.getAnswer()).contains(chunk.getContent()).doesNotContain("police", "plaque");
        assertThat(response.getSources().get(0).getSnippet()).isEqualTo(chunk.getContent());
        assertThat(response.getConversationId()).isNotBlank();
        verifyNoInteractions(gemini);
    }

    @Test void unavailableWithoutSourcesReturnsUnavailableAndPersistentConversationId() {
        when(embeddings.generateEmbedding(anyString())).thenThrow(new AiServiceException("Unavailable"));
        when(store.searchSimilar(anyString(), any(), anyInt(), anyDouble())).thenReturn(List.of());
        RagResponse response = service.query(new RagQueryRequest(null, "Point inaccessible ?"), "driver");
        assertThat(response.getStatus()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(response.getConversationId()).isNotBlank();
        verify(histories).save(any());
    }

    @Test void foreignConversationRejectedBeforeAnyProviderCall() {
        when(histories.findById("other")).thenReturn(Optional.of(RagConversation.builder().id("other").userId("someone-else").build()));
        assertThatThrownBy(() -> service.query(new RagQueryRequest("other", "Question"), "driver"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        verifyNoInteractions(embeddings, store, gemini);
        verify(histories, never()).save(any());
    }

    @Test void inventedCitationFallsBackToExistingExcerpts() {
        when(embeddings.generateEmbedding(anyString())).thenReturn(List.of(1f));
        retrievedSource();
        when(gemini.generateJson(anyString())).thenReturn("{\"status\":\"ANSWERED\",\"answer\":\"Contacter la police\",\"citations\":[{\"chunkId\":\"source-1\",\"quote\":\"police\"}]}");
        RagResponse response = service.query(new RagQueryRequest(null, "Point inaccessible ?"), "driver");
        assertThat(response.getStatus()).isEqualTo("SOURCE_EXCERPTS");
        assertThat(response.getAnswer()).doesNotContain("police");
    }

    @Test void supportedAnswerReturnsOnlyValidatedCitations() {
        when(embeddings.generateEmbedding(anyString())).thenReturn(List.of(1f));
        retrievedSource();
        when(gemini.generateJson(anyString())).thenReturn("{\"status\":\"ANSWERED\",\"answer\":\"Consulter le superviseur.\",\"citations\":[{\"chunkId\":\"source-1\",\"quote\":\"consulter le superviseur\"}]}");
        RagResponse response = service.query(new RagQueryRequest(null, "Point inaccessible ?"), "driver");
        assertThat(response.getStatus()).isEqualTo("ANSWERED");
        assertThat(response.getSources()).hasSize(1);
        assertThat(response.getSources().get(0).getSnippet()).isEqualTo("consulter le superviseur");
    }

    @Test void independentShortQuestionDoesNotRetrievePreviousPhotoTopicDuringOutage() {
        String previous = "La photo APRÈS est-elle nécessaire ?";
        String salary = "Quel est le salaire annuel ?";
        when(histories.findById("existing")).thenReturn(Optional.of(RagConversation.builder().id("existing").userId("driver")
                .messages(new ArrayList<>(List.of(RagMessage.builder().sender("USER").content(previous).build()))).build()));
        when(embeddings.generateEmbedding(anyString())).thenThrow(new AiServiceException("Unavailable"));
        when(store.searchSimilar(eq(salary), any(), anyInt(), anyDouble())).thenReturn(List.of());
        RagResponse response = service.query(new RagQueryRequest("existing", salary), "driver");
        verify(embeddings).generateEmbedding(salary);
        verify(store).searchSimilar(eq(salary), isNull(), anyInt(), anyDouble());
        assertThat(response.getStatus()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(response.getSources()).isEmpty();
        assertThat(response.getAnswer()).doesNotContain("photo");
        verifyNoInteractions(gemini);
    }

    @Test void explicitAnaphoricFollowUpRetainsPreviousUserTopic() {
        String previous = "La photo APRÈS est-elle nécessaire ?";
        String followUp = "Et si ce point est inaccessible ?";
        when(histories.findById("existing")).thenReturn(Optional.of(RagConversation.builder().id("existing").userId("driver")
                .messages(new ArrayList<>(List.of(RagMessage.builder().sender("USER").content(previous).build()))).build()));
        when(embeddings.generateEmbedding(anyString())).thenThrow(new AiServiceException("Unavailable"));
        retrievedSource();
        RagResponse response = service.query(new RagQueryRequest("existing", followUp), "driver");
        verify(embeddings).generateEmbedding(previous + "\n" + followUp);
        verify(store).searchSimilar(eq(previous + "\n" + followUp), isNull(), anyInt(), anyDouble());
        assertThat(response.getStatus()).isEqualTo("SOURCE_EXCERPTS");
        assertThat(response.getSources().get(0).getSnippet()).isEqualTo(chunk.getContent());
        verifyNoInteractions(gemini);
    }
}
