package com.municipalite.gestiontournees.ai.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité MongoDB conservant l'historique des conversations RAG par utilisateur.
 */
@Document(collection = "rag_conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagConversation {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String title;

    @Builder.Default
    private List<RagMessage> messages = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
