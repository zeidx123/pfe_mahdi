package com.municipalite.gestiontournees.tournee.dto;

import com.municipalite.gestiontournees.tournee.model.StatutTournee;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class TourneeRequest {

    @NotBlank(message = "La référence est obligatoire")
    private String reference;

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    private String description;

    @NotNull(message = "La date prévue est obligatoire")
    private LocalDate datePrevue;

    private LocalTime heureDepartPrevue;

    @NotBlank(message = "Un camion doit être assigné")
    private String camionId;

    @NotBlank(message = "Un chauffeur doit être assigné")
    private String chauffeurId;

    private List<String> pointsCollecteIds;

    private StatutTournee statut = StatutTournee.PLANIFIEE;

    private String notes;
}
