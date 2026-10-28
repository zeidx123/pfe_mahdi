package com.municipalite.gestiontournees.tournee.model;

import lombok.*;
import java.time.LocalDateTime;

/** One visit in this tour, independent of the collection point's global state. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TourneeVisit {
    public enum Status { PENDING, ARRIVED, COMPLETED_REPORTED, ISSUE_REPORTED }
    private String pointId;
    private int order;
    @Builder.Default private Status status = Status.PENDING;
    private int plannedServiceSeconds;
    private LocalDateTime arrivedAt;
    private LocalDateTime completedAt;
    private String beforePhoto;
    private String afterPhoto;
    private String legacyPhoto;
    private boolean legacyProgressMissing;
    private String analysisId;
    private String analysisStatus;
    // Local engine only (null for LLM analyses). analysisDecision orders human review; it never changes this visit.
    private java.util.Map<String, Double> analysisProbabilities;
    private String analysisDecision;
    private String analysisQualityIssue;
    private String driverOutcome;
    private String driverAssessment;
    private String notes;
    private String reviewLabel;
    private String reviewNotes;
    private String reviewedBy;
    private LocalDateTime reviewedAt;

    public boolean handled() {
        return status == Status.COMPLETED_REPORTED || status == Status.ISSUE_REPORTED;
    }
}
