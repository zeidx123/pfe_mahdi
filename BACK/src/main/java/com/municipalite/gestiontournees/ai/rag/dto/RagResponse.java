package com.municipalite.gestiontournees.ai.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagResponse {
    private String conversationId;
    private String question;
    private String answer;
    private List<SourceDto> sources;
    private boolean relevantContextFound;
    private double maxSimilarityScore;
}
