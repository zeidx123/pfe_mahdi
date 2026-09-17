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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class ScoredChunk {
        private DocumentChunk chunk;
        private double score; // Score de similarité combiné (0.0 à 1.0)
    }

    void saveChunk(DocumentChunk chunk);

    void saveChunks(List<DocumentChunk> chunks);

    List<ScoredChunk> searchSimilar(String queryText, List<Float> queryEmbedding, int topK, double minSimilarityThreshold);

    void deleteByDocumentId(String documentId);
}
