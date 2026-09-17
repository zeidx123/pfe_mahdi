package com.municipalite.gestiontournees.tournee.dto;

import com.municipalite.gestiontournees.pointcollecte.dto.PointCollecteResponse;
import com.municipalite.gestiontournees.tournee.model.StatutTournee;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TourneeResponse {
    private String id;
    private String reference;
    private String nom;
    private String description;
    private LocalDate datePrevue;
    private LocalTime heureDepartPrevue;

    // IDs + infos de l'affectation pour affichage
    private String camionId;
    private String camionMatricule;
    private String chauffeurId;
    private String chauffeurNomComplet;

    private List<String> pointsCollecteIds;
    private List<PointCollecteResponse> pointsCollecte; // Détails complets pour le mobile
    private Integer nombrePoints;

    private StatutTournee statut;
    private LocalDateTime heureDebutReelle;
    private LocalDateTime heureFinReelle;
    private Double distanceParcourue;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // === Résultat Optimisation IA ===
    private List<String> pointsCollecteIdsOrdonnes;
    private List<String> pointsAdressesOrdonnes;
    private Double distanceOptimaleKm;
    private Integer dureeEstimeeMinutes;
    private String dureeFormatee;
    private String explicationOrdre;
    private Boolean optimisationEffectuee;

    // Photos terrain
    private java.util.Map<String, String> photosPoints;
    private String photoFin;
}
