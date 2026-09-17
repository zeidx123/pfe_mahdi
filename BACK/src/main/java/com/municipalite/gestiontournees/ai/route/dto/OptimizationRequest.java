package com.municipalite.gestiontournees.ai.route.dto;

import lombok.Data;

import java.util.List;

/**
 * Requête d'optimisation d'une tournée envoyée par le superviseur.
 */
@Data
public class OptimizationRequest {
    private String camionId;
    private String chauffeurId;
    private List<String> pointCollecteIds; // IDs des points à visiter
    private Double depotLatitude;          // Coordonnées du dépôt (départ/retour)
    private Double depotLongitude;
    private String dateTournee;            // Date prévue (ISO string)
}
