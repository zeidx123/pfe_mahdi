package com.municipalite.gestiontournees.ai.rag.dto;

import com.municipalite.gestiontournees.ai.rag.model.AiDocument.DocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentInfoDto {
    private String id;
    private String originalName;
    private String fileType;
    private long fileSize;
    private DocumentStatus status;
    private int chunkCount;
    private String errorMessage;
    private String uploadedBy;
    private LocalDateTime createdAt;
    private LocalDateTime indexedAt;
}
