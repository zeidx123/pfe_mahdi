package com.municipalite.gestiontournees.tracking.model;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehiculePosition {
    @NotBlank
    private String camionId;
    private String camionMatricule;
    @NotBlank
    private String tourneeId;
    private String tourneeReference;
    @NotNull @DecimalMin("-90") @DecimalMax("90")
    private Double latitude;
    @NotNull @DecimalMin("-180") @DecimalMax("180")
    private Double longitude;
    @DecimalMin("0")
    private Double vitesse;       // km/h
    @DecimalMin("0") @DecimalMax("100")
    private Double niveauCarburant; // %
    private String statut;        // EN_ROUTE, A_LARRET, EN_COLLECTE
    private LocalDateTime timestamp;
    private boolean stale;
}
