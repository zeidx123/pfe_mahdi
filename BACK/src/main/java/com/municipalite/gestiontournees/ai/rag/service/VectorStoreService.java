package com.municipalite.gestiontournees.ai.rag.service;

import com.municipalite.gestiontournees.ai.rag.model.DocumentChunk;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Interface d'abstraction pour la base de données vectorielle (Vector Store).
 */
public interface VectorStoreService {

    enum RetrievalMode { LEXICAL, SEMANTIC, HYBRID }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class ScoredChunk {
        private DocumentChunk chunk;
        private double score;
        private String retrievalMode;

        public ScoredChunk(DocumentChunk chunk, double score) { this(chunk, score, null); }
    }

    void saveChunk(DocumentChunk chunk);

    void saveChunks(List<DocumentChunk> chunks);

    List<ScoredChunk> searchSimilar(String queryText, List<Float> queryEmbedding, int topK, double minSimilarityThreshold);

    void deleteByDocumentId(String documentId);

    int usableChunkCount(String documentId);
}
