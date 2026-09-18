package com.municipalite.gestiontournees.reclamation.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @NotBlank @Size(max = 200)
    private String titre;
    @NotBlank @Pattern(regexp = "PANNE_MECANIQUE|ROUTE_BLOQUEE|BAC_DEGRADE|AUTRE")
    private String type; // PANNE_MECANIQUE, ROUTE_BLOQUEE, BAC_DEGRADE, AUTRE
    @NotBlank @Size(max = 5000)
    private String description;
    private String chauffeurId;
    private String chauffeurNom;
    private String statut; // EN_ATTENTE, TRAITEE
    private String reponse;
    private LocalDateTime dateCreation;
    private LocalDateTime dateTraitement;
}
