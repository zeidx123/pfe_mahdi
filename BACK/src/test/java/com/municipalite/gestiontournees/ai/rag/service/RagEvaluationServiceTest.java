package com.municipalite.gestiontournees.ai.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.common.*;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.rag.dto.*;
import com.municipalite.gestiontournees.ai.rag.model.*;
import com.municipalite.gestiontournees.ai.rag.repository.*;
import com.municipalite.gestiontournees.ai.rag.service.VectorStoreService.RetrievalMode;
import com.municipalite.gestiontournees.security.AccessService;
import com.municipalite.gestiontournees.tournee.repository.TourneeRepository;
import com.municipalite.gestiontournees.user.model.*;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagEvaluationServiceTest {
    AiProperties properties = new AiProperties();
    DocumentChunkRepository chunks = mock(DocumentChunkRepository.class);
    AiDocumentRepository documents = mock(AiDocumentRepository.class);
    RagConversationRepository histories = mock(RagConversationRepository.class);
    EmbeddingService embeddings = mock(EmbeddingService.class);
    GeminiClient gemini = mock(GeminiClient.class);
    MongoVectorStoreService store = new MongoVectorStoreService(chunks, documents, properties);
    RagService rag = new RagService(embeddings, store, gemini, histories, properties, new ObjectMapper());
    RagEvaluationService evaluation = new RagEvaluationService(new AccessService(mock(TourneeRepository.class)),
            store, embeddings, rag, properties, new ObjectMapper());

    @BeforeEach void setup() {
        properties.getRag().setEmbeddingDimensions(2);
        properties.getRag().setTopK(3);
        authenticate(Role.SUPERVISEUR);
        when(documents.findAll()).thenReturn(List.of(document("approved", true), document("private", false)));
        when(chunks.findAll()).thenReturn(List.of(chunk("visible", "approved", "photo après obligatoire", true),
                chunk("hidden", "private", "secret photo", true)));
        when(embeddings.generateEmbedding(anyString())).thenReturn(List.of(1f, 0f));
        when(gemini.generateJson(anyString())).thenReturn("{\"status\":\"ANSWERED\",\"answer\":\"Photo après obligatoire.\","
                + "\"citations\":[{\"chunkId\":\"visible\",\"quote\":\"photo après obligatoire\"}]}");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void threeModesUseSameApprovedSnapshotAndOneEmbeddingWithoutWritingHistory() {
        var response = evaluation.evaluate(request(true));
        assertThat(response.corpus().chunkCount()).isEqualTo(1);
        assertThat(response.results()).extracting(RagEvaluationResponse.ModeResult::requestedMode)
                .containsExactly("LEXICAL", "SEMANTIC", "HYBRID");
        assertThat(response.results()).allSatisfy(result -> {
            assertThat(result.retrievalStatus()).isEqualTo("AVAILABLE");
            assertThat(result.status()).isEqualTo("ANSWERED");
            assertThat(result.retrievedSources()).extracting(SourceDto::getChunkId).containsExactly("visible");
            assertThat(result.citations().get(0).getSnippet()).isEqualTo("photo après obligatoire");
        });
        verify(chunks, times(1)).findAll(); verify(documents, times(1)).findAll();
        verify(embeddings, times(1)).generateEmbedding("photo après ?");
        verify(gemini, times(3)).generateJson(anyString());
        verifyNoInteractions(histories);
    }

    @Test void driverIsDeniedBeforeReadingCorpusOrCallingProvider() {
        authenticate(Role.CHAUFFEUR);
        assertThatThrownBy(evaluation::corpus).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> evaluation.evaluate(request(true))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(chunks, documents, embeddings, gemini);
    }

    @Test void lexicalOnlyDoesNotCallEmbeddingAndGenerationIsOptIn() {
        var request = request(false); request.setModes(List.of(RetrievalMode.LEXICAL));
        var result = evaluation.evaluate(request).results().get(0);
        assertThat(result.generationStatus()).isEqualTo("NOT_REQUESTED");
        assertThat(result.status()).isNull();
        assertThat(result.retrievedSources()).hasSize(1);
        verifyNoInteractions(embeddings, gemini, histories);
    }

    @Test void anonymousAccessCannotExportTheCorpus() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(evaluation::corpus)
                .isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(chunks, documents, embeddings, gemini);
    }

    @Test void embeddingOutageDoesNotBecomeSuccessfulSemanticOrHybridComparison() {
        when(embeddings.generateEmbedding(anyString())).thenThrow(new AiServiceException("provider failed"));
        var results = evaluation.evaluate(request(true)).results();
        assertThat(results.get(0).status()).isEqualTo("ANSWERED");
        assertThat(results.get(1).retrievalStatus()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(results.get(1).retrievedSources()).isEmpty();
        assertThat(results.get(1).status()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(results.get(2).effectiveMode()).isEqualTo("LEXICAL");
        assertThat(results.get(2).retrievalStatus()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(results.get(2).status()).isEqualTo("SOURCE_EXCERPTS");
        verify(gemini, times(1)).generateJson(anyString());
    }

    @Test void incompleteAndIncompatibleIndexesAreVisibleAndFingerprintGuardsCorpusDrift() {
        String fingerprint = evaluation.corpus().corpus().fingerprint();
        var incompatible = chunk("legacy", "approved", "photo ancienne", false);
        when(chunks.findAll()).thenReturn(List.of(chunk("visible", "approved", "photo après obligatoire", true), incompatible));
        var request = request(false); request.setExpectedCorpusFingerprint(fingerprint);
        assertThatThrownBy(() -> evaluation.evaluate(request)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        verifyNoInteractions(embeddings, gemini);
        request.setExpectedCorpusFingerprint(null);
        var response = evaluation.evaluate(request);
        assertThat(response.corpus().compatibleChunkCount()).isEqualTo(1);
        assertThat(response.results().get(1).retrievalStatus()).isEqualTo("INDEX_INCOMPLETE");
        when(chunks.findAll()).thenReturn(List.of(incompatible));
        assertThat(evaluation.evaluate(request).embeddingStatus()).isEqualTo("INDEX_NOT_READY");
    }

    @Test void followUpUsesFixedHistoryButUnrelatedInvalidCitationsDoNotBecomeExcerpts() {
        when(gemini.generateJson(anyString())).thenReturn("{\"status\":\"ANSWERED\",\"answer\":\"invented\",\"citations\":[{\"chunkId\":\"private\",\"quote\":\"secret\"}]}");
        var request = request(true); request.setQuestion("Et si ce point est inaccessible ?");
        request.setHistory(List.of(new RagEvaluationRequest.HistoryTurn("USER", "photo après ?")));
        var result = evaluation.evaluate(request);
        assertThat(result.retrievalQuery()).startsWith("photo après ?\n");
        assertThat(result.results()).allSatisfy(mode -> {
            assertThat(mode.generationStatus()).isEqualTo("INVALID_RESPONSE");
            assertThat(mode.status()).isEqualTo("INSUFFICIENT_INFORMATION");
            assertThat(mode.citations()).isEmpty();
            assertThat(mode.answer()).doesNotContain("invented", "secret");
        });
        verifyNoInteractions(histories);
    }

    static AiDocument document(String id, boolean approved) {
        return AiDocument.builder().id(id).approved(approved).status(AiDocument.DocumentStatus.INDEXED).build();
    }
    DocumentChunk chunk(String id, String document, String text, boolean compatible) {
        return DocumentChunk.builder().id(id).documentId(document).documentName(document).content(text)
                .embedding(List.of(1f, 0f)).embeddingDimensions(2).embeddingModel(compatible ? properties.getRag().getEmbeddingModel() : "retired-model")
                .embeddingVersion(properties.getRag().getEmbeddingVersion()).build();
    }
    static RagEvaluationRequest request(boolean generate) {
        var request = new RagEvaluationRequest(); request.setQuestion("photo après ?"); request.setGenerateAnswer(generate); return request;
    }
    static void authenticate(Role role) {
        User user = new User(); user.setId("reviewer"); user.setRole(role); user.setActif(true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
