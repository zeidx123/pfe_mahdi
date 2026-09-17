package com.municipalite.gestiontournees.pointcollecte.dto;

import com.municipalite.gestiontournees.pointcollecte.model.TypeDechet;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PointCollecteRequest {

    @NotBlank(message = "La référence est obligatoire")
    private String reference;

    @NotBlank(message = "L'adresse est obligatoire")
    private String adresse;

    private String zone;

    @NotNull(message = "La latitude est obligatoire")
    private Double latitude;

    @NotNull(message = "La longitude est obligatoire")
    private Double longitude;

    private TypeDechet typeDechet = TypeDechet.GENERAL;

    private Integer niveauRemplissage = 0;

    private boolean actif = true;
}
