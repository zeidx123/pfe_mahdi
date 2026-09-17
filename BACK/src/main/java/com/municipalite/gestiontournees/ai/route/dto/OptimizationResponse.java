package com.municipalite.gestiontournees.ai.route.dto;

import com.municipalite.gestiontournees.pointcollecte.dto.PointCollecteResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Réponse de l'optimisation : ordre optimisé + métriques + explications.
 */
@Data
@Builder
public class OptimizationResponse {
    private String optimizationId;
    private List<PointCollecteResponse> ordreOptimise; // Points dans l'ordre optimisé
    private Double distanceTotaleKm;
    private Integer dureeEstimeesMinutes;
    private Double poidsEstimeKg;
    private Double capaciteCamionKg;
    private Integer tauxRemplissagePct;
    private Double consommationEstimeeLitres;
    private String explication;
    private String recommandations;
    private String algorithmeUtilise;
}
