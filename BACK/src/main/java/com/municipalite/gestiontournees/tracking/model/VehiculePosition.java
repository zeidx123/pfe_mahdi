package com.municipalite.gestiontournees.tracking.model;

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
    private String camionId;
    private String camionMatricule;
    private String tourneeId;
    private String tourneeReference;
    private Double latitude;
    private Double longitude;
    private Double vitesse;       // km/h
    private Double niveauCarburant; // %
    private String statut;        // EN_ROUTE, A_LARRET, EN_COLLECTE
    private LocalDateTime timestamp;
}
