package com.municipalite.gestiontournees.tournee.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Document(collection = "tournees")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tournee {

    @Id
    private String id;

    @Indexed(unique = true)
    private String reference; // ex: T-2024-001

    private String nom;
    private String description;
    private LocalDate datePrevue;
    private LocalTime heureDepartPrevue;

    // Affectations
    private String camionId;
    private String chauffeurId;

    // Liste des points de collecte dans l'ordre
    private List<String> pointsCollecteIds;

    private StatutTournee statut;

    // Données de réalisation
    private LocalDateTime heureDebutReelle;
    private LocalDateTime heureFinReelle;
    private Double distanceParcourue; // en km

    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // === Résultat Optimisation IA ===
    private List<String> pointsCollecteIdsOrdonnes; // Ordre optimisé par l'IA
    private List<String> pointsAdressesOrdonnes;    // Adresses dans l'ordre optimal
    private Double distanceOptimaleKm;              // Distance totale optimisée (km)
    private Integer dureeEstimeeMinutes;            // Durée estimée en minutes
    private String dureeFormatee;                   // ex: "1h 35min"
    private String explicationOrdre;                // Explication de la trajectoire
    private Boolean optimisationEffectuee;          // true si l'IA a pu calculer le trajet

    // Photos prises par l'agent sur le terrain
    // Clé = pointCollecteId, Valeur = nom du fichier photo
    @Builder.Default
    private java.util.Map<String, String> photosPoints = new java.util.HashMap<>();

    // Photo prise à la fin de la tournée
    private String photoFin;
    @Builder.Default
    private List<TourneeVisit> visits = new java.util.ArrayList<>();
    private RoadRoute route;
    private String routeError;
    private boolean returnedToDepot;
    private Double estimatedAtStartSeconds;
    private Double estimatedServiceAtStartSeconds;
}
