package com.municipalite.gestiontournees.ai.rag.service;

import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.rag.model.*;
import com.municipalite.gestiontournees.ai.rag.repository.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MongoVectorStoreServiceTest {
    DocumentChunkRepository chunks = mock(DocumentChunkRepository.class);
    AiDocumentRepository documents = mock(AiDocumentRepository.class);
    AiProperties properties = new AiProperties();
    MongoVectorStoreService store = new MongoVectorStoreService(chunks, documents, properties);

    @BeforeEach void setup() {
        properties.getRag().setEmbeddingDimensions(2);
        when(documents.findAll()).thenReturn(List.of(AiDocument.builder().id("approved").approved(true).status(AiDocument.DocumentStatus.TEXT_READY).build()));
    }

    DocumentChunk chunk(String id, String text) { return DocumentChunk.builder().id(id).documentId("approved").content(text).build(); }

    @Test void bm25UsesWordsAndDoesNotLeakUnapprovedDocuments() {
        DocumentChunk notApproved = chunk("private", "Obstacle obstacle obstacle"); notApproved.setDocumentId("unapproved");
        when(chunks.findAll()).thenReturn(List.of(chunk("relevant", "Un obstacle empêche la collecte."), chunk("substring", "Une obstruction nécessite inspection."), notApproved));
        assertThat(store.search("obstacle", null, 5, .7, VectorStoreService.RetrievalMode.LEXICAL))
                .extracting(r -> r.getChunk().getId()).containsExactly("relevant");
    }

    @Test void mismatchedOrLegacyEmbeddingsNeverParticipateInSemanticResults() {
        DocumentChunk legacy = chunk("legacy", "Autre texte"); legacy.setEmbedding(List.of(1f, 0f));
        DocumentChunk wrong = chunk("wrong", "Autre texte"); wrong.setEmbedding(List.of(1f)); wrong.setEmbeddingModel(properties.getRag().getEmbeddingModel());
        wrong.setEmbeddingDimensions(1); wrong.setEmbeddingVersion(properties.getRag().getEmbeddingVersion());
        when(chunks.findAll()).thenReturn(List.of(legacy, wrong));
        assertThat(store.search("collecte", List.of(1f, 0f), 5, .7, VectorStoreService.RetrievalMode.SEMANTIC)).isEmpty();
        assertThat(MongoVectorStoreService.cosine(List.of(1f), List.of(1f, 1f))).isZero();
    }

    @Test void compatibleVectorsWorkAndHybridRanksAgreeingEvidenceFirst() {
        DocumentChunk supported = chunk("supported", "Obstacle visible"); supported.setEmbedding(List.of(1f, 0f));
        supported.setEmbeddingModel(properties.getRag().getEmbeddingModel()); supported.setEmbeddingDimensions(2); supported.setEmbeddingVersion(properties.getRag().getEmbeddingVersion());
        when(chunks.findAll()).thenReturn(List.of(chunk("lexical", "Obstacle présent sur le site de collecte"), supported));
        var results = store.search("obstacle", List.of(1f, 0f), 5, .7, VectorStoreService.RetrievalMode.HYBRID);
        assertThat(results.get(0).getChunk().getId()).isEqualTo("supported");
        assertThat(results.get(0).getRetrievalMode()).isEqualTo("HYBRID");
    }

    @Test void accentedQueryMatchesSourceAndEmptyQueryDoesNotRetrieveEverything() {
        when(chunks.findAll()).thenReturn(List.of(chunk("safety", "Sécurité : conserver les gants")));
        assertThat(store.searchSimilar("securite", null, 3, .7)).hasSize(1);
        assertThat(store.searchSimilar("que dois je faire", null, 3, .7)).isEmpty();
    }

    @Test void recoveryCountsOnlyStoredUsableTextChunks() {
        when(chunks.findByDocumentId("approved")).thenReturn(List.of(chunk("text", "Source disponible"),
                chunk("blank", "   "), chunk("null", null)));
        assertThat(store.usableChunkCount("approved")).isEqualTo(1);
    }
}
