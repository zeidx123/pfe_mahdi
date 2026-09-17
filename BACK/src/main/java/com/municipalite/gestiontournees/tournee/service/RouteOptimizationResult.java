package com.municipalite.gestiontournees.tournee.service;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Résultat de l'optimisation IA d'une tournée de collecte.
 */
@Data
@Builder
public class RouteOptimizationResult {

    /** IDs des points dans l'ordre optimal calculé */
    private List<String> pointsIdsOrdonnes;

    /** Adresses/références des points dans l'ordre optimal */
    private List<String> pointsAdresses;

    /** Distances en km entre chaque étape successive */
    private List<Double> distancesEtapes;

    /** Distance totale de la tournée en km */
    private double distanceTotaleKm;

    /** Durée totale estimée en minutes */
    private int dureeEstimeeMinutes;

    /** Durée formatée lisible ex: "1h 35min" */
    private String dureeFormatee;

    /** Explication détaillée en langage naturel pour la justification de l'ordre */
    private String explicationOrdre;

    /** Indique si l'optimisation a bien été effectuée (false = pas de GPS) */
    private boolean optimise;

    public static RouteOptimizationResult empty() {
        return RouteOptimizationResult.builder()
                .pointsIdsOrdonnes(new ArrayList<>())
                .pointsAdresses(new ArrayList<>())
                .distancesEtapes(new ArrayList<>())
                .distanceTotaleKm(0.0)
                .dureeEstimeeMinutes(0)
                .dureeFormatee("0 min")
                .explicationOrdre("Pas assez de points GPS pour calculer une explication.")
                .optimise(false)
                .build();
    }
}
