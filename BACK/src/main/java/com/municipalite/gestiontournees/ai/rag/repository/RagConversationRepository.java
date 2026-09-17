package com.municipalite.gestiontournees.ai.rag.repository;

import com.municipalite.gestiontournees.ai.rag.model.RagConversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RagConversationRepository extends MongoRepository<RagConversation, String> {
    List<RagConversation> findByUserIdOrderByUpdatedAtDesc(String userId);
}
