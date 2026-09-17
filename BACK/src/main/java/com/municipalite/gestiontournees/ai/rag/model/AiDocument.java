package com.municipalite.gestiontournees.ai.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Entité représentant un document importé dans la base documentaire RAG.
 */
@Document(collection = "ai_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDocument {

    @Id
    private String id;

    private String originalName;
    private String fileType; // PDF, DOCX, TXT
    private long fileSize; // en octets
    private String filePath;
    
    private DocumentStatus status; // UPLOADED, PROCESSING, INDEXED, FAILED
    private int chunkCount;
    private String errorMessage;
    
    private String uploadedBy;
    private LocalDateTime createdAt;
    private LocalDateTime indexedAt;

    public enum DocumentStatus {
        UPLOADED,
        PROCESSING,
        INDEXED,
        FAILED
    }
}
