package com.municipalite.gestiontournees.auth.dto;

import com.municipalite.gestiontournees.user.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String type = "Bearer";
    private String id;
    private String email;
    private String nom;
    private String prenom;
    private Role role;
    private String photo;
}
