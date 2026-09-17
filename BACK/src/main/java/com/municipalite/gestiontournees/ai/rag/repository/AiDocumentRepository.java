package com.municipalite.gestiontournees.ai.rag.repository;

import com.municipalite.gestiontournees.ai.rag.model.AiDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiDocumentRepository extends MongoRepository<AiDocument, String> {
    List<AiDocument> findAllByOrderByCreatedAtDesc();
}
