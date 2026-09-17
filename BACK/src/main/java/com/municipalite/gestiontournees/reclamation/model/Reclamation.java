package com.municipalite.gestiontournees.reclamation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "reclamations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reclamation {
    @Id
    private String id;
    private String titre;
    private String type; // PANNE_MECANIQUE, ROUTE_BLOQUEE, BAC_DEGRADE, AUTRE
    private String description;
    private String chauffeurId;
    private String chauffeurNom;
    private String statut; // EN_ATTENTE, TRAITEE
    private String reponse;
    private LocalDateTime dateCreation;
    private LocalDateTime dateTraitement;
}
