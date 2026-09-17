package com.municipalite.gestiontournees.ai.assistant.repository;

import com.municipalite.gestiontournees.ai.assistant.model.ChatConversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatConversationRepository extends MongoRepository<ChatConversation, String> {
    List<ChatConversation> findBySupervisorEmailOrderByUpdatedAtDesc(String supervisorEmail);
}
