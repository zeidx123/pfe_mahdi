package com.municipalite.gestiontournees.camion.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Document(collection = "camions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Camion {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
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
