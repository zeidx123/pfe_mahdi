package com.municipalite.gestiontournees.ai.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Représente une tranche (chunk) de texte extrait d'un document avec son vecteur d'embedding.
 */
@Document(collection = "document_chunks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    @Id
    private String id;

    @Indexed
    private String documentId;
    private String documentName;
    private String fileType;

    private int pageNumber;
    private int chunkIndex;
    private String sectionTitle;

    private String content;

    /**
     * Vecteur d'embedding (768 dimensions pour Gemini text-embedding-004 ou 1536 pour OpenAI).
     */
    private List<Float> embedding;

    private LocalDateTime createdAt;
}
