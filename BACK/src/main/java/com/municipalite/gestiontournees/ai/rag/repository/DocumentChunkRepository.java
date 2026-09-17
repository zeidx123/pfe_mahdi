package com.municipalite.gestiontournees.ai.rag.repository;

import com.municipalite.gestiontournees.ai.rag.model.DocumentChunk;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends MongoRepository<DocumentChunk, String> {
    List<DocumentChunk> findByDocumentId(String documentId);
    void deleteByDocumentId(String documentId);
}
