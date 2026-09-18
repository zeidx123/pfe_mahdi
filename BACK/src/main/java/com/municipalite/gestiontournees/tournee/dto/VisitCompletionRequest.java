package com.municipalite.gestiontournees.tournee.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data
public class VisitCompletionRequest {
    public enum Outcome { COMPLETED, ISSUE }
    @NotNull private Outcome outcome;
    @Size(max = 2000) private String notes;
    private String driverAssessment;
}
