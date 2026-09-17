package com.municipalite.gestiontournees.agent.dto;

import com.municipalite.gestiontournees.user.model.Role;
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
public class AgentResponse {
    private String id;
    private String matricule;
    private String nom;
    private String prenom;
    private String cin;
    private String email;
    private String telephone;
    private String adresse;
    private String numeroPermis;
    private LocalDate dateExpirationPermis;
    private String typePermis;
    private LocalDate dateEmbauche;
    private String photo;
    private Role role;
    private boolean actif;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
