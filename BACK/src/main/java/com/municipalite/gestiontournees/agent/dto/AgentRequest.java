package com.municipalite.gestiontournees.agent.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AgentRequest {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    private String prenom;

    @NotBlank(message = "Le CIN est obligatoire")
    private String cin;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    private String email;

    @NotBlank(message = "Le téléphone est obligatoire")
    private String telephone;

    private String adresse;
    
    private String numeroPermis;
    
    private LocalDate dateExpirationPermis;
    
    private String typePermis;
    
    @NotNull(message = "La date d'embauche est obligatoire")
    private LocalDate dateEmbauche;
    
    private String photo;
    
    private boolean actif = true;
}
