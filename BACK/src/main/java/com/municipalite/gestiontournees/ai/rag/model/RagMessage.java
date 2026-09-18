package com.municipalite.gestiontournees.ai.rag.model;

import com.municipalite.gestiontournees.ai.rag.dto.SourceDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Message individuel dans une conversation RAG.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagMessage {
    private String id;
    private String sender; // "USER" ou "ASSISTANT"
    private String content;
    private String status;
    private List<SourceDto> sources;
    private Double highestScore;
    private LocalDateTime timestamp;
}
