package com.municipalite.gestiontournees.tournee.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
@Data
public class VisitReviewRequest {
    @NotBlank private String label;
    @Size(max = 2000) private String notes;
}
