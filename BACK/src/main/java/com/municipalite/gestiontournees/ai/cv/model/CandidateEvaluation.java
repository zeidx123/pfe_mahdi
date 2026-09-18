package com.municipalite.gestiontournees.ai.cv.model;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CandidateEvaluation {
    private DriverRequirements requirements;
    private Map<String, FieldEvidence> evaluatedFields;
    private List<CriterionScore> criteria;
    private int score;
    private String requiredCriteriaStatus;
    private boolean provisional;
    private LocalDateTime evaluatedAt;
}
