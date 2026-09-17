package com.municipalite.gestiontournees.camion.dto;

import com.municipalite.gestiontournees.camion.model.EtatCamion;
import com.municipalite.gestiontournees.camion.model.StatutCamion;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CamionRequest {

    @NotBlank(message = "Le matricule est obligatoire")
    private String matricule;

    @NotBlank(message = "La marque est obligatoire")
    private String marque;

    private String modele;
    private Integer annee;
    private String typeCamion;
    private Double capacitePoids;
    private Double capaciteReservoir;
    private Double kilometrage;
    private LocalDate dateMiseEnService;

    private StatutCamion statut = StatutCamion.DISPONIBLE;
    private EtatCamion etat = EtatCamion.BON;

    private String numeroESP32;
    private LocalDate dateDerniereMaintenance;
    private LocalDate prochaineMaintenance;

    private boolean actif = true;
}
