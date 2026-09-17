package com.municipalite.gestiontournees.camion.dto;

import com.municipalite.gestiontournees.camion.model.EtatCamion;
import com.municipalite.gestiontournees.camion.model.StatutCamion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CamionResponse {
    private String id;
    private String matricule;
    private String marque;
    private String modele;
    private Integer annee;
    private String typeCamion;
    private Double capacitePoids;
    private Double capaciteReservoir;
    private Double kilometrage;
    private LocalDate dateMiseEnService;
    private StatutCamion statut;
    private EtatCamion etat;
    private String numeroESP32;
    private LocalDate dateDerniereMaintenance;
    private LocalDate prochaineMaintenance;
    private boolean actif;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
