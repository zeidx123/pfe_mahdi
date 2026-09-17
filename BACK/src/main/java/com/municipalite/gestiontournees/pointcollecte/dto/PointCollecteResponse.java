package com.municipalite.gestiontournees.pointcollecte.dto;

import com.municipalite.gestiontournees.pointcollecte.model.TypeDechet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointCollecteResponse {
    private String id;
    private String reference;
    private String adresse;
    private String zone;
    private Double latitude;
    private Double longitude;
    private TypeDechet typeDechet;
    private Integer niveauRemplissage;
    private boolean actif;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
