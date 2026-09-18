package com.municipalite.gestiontournees.ai.cv.model;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CriterionScore {
    private String key;
    private String label;
    private String value;
    private boolean required;
    private String requiredStatus; // MET, UNMET, NEEDS_REVIEW, NOT_REQUIRED
    private String evidenceStatus;
    private double points;
    private int maxPoints;
    private String explanation;
}
