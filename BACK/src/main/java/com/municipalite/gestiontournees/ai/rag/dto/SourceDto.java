package com.municipalite.gestiontournees.ai.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO représentant une source citée dans la réponse RAG.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceDto {
    private String documentId;
    private String documentName;
    private int pageNumber;
    private String sectionTitle;
    private String chunkId;
    private double score;
    private String snippet;
    private String sourceUrl;
}
