package com.municipalite.gestiontournees.ai.route.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entité représentant le résultat d'une optimisation de tournée par l'IA.
 */
@Document(collection = "route_optimizations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteOptimization {

    @Id
    private String id;

    private String camionId;
    private String chauffeurId;
    private LocalDateTime dateTournee;

    // Points dans l'ordre optimisé (IDs)
    private List<String> ordreOptimiseIds;

    // Métriques calculées
    private Double distanceTotaleKm;
    private Integer dureeEstimeesMinutes;
    private Double poidsEstimeKg;
    private Double capaciteCamionKg;
    private Integer tauxRemplissagePct;
    private Double consommationEstimeeLitres;

    // Explications IA
    private String explication;
    private String recommandations;
    private String algorithmeUtilise;

    // Statut validation superviseur
    private String statut; // PROPOSE, VALIDE, REJETE, MODIFIE
    private LocalDateTime dateCreation;
    private LocalDateTime dateValidation;
}
