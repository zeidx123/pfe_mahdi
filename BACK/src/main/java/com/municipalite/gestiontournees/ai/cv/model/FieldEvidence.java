package com.municipalite.gestiontournees.ai.cv.model;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder(toBuilder = true) @NoArgsConstructor @AllArgsConstructor
public class FieldEvidence {
    private String value;
    private String status; // VERIFIED, MISSING, INVALID, UNCERTAIN
    private String snippet;
    private Integer pageNumber;
    private String location;
    private String source; // MODEL, LOCAL, MANUAL
    private String reviewNote;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
}
