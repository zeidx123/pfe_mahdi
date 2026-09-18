package com.municipalite.gestiontournees.pointcollecte.dto;

import com.municipalite.gestiontournees.pointcollecte.model.TypeDechet;
import jakarta.validation.constraints.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PointCollecteRequest {
    public interface Create {}

    @NotBlank(message = "La référence est obligatoire", groups = Create.class)
    private String reference;

    @NotBlank(message = "L'adresse est obligatoire", groups = Create.class)
    private String adresse;

    private String zone;

    @NotNull(message = "La latitude est obligatoire", groups = Create.class)
    @DecimalMin("-90") @DecimalMax("90")
    private Double latitude;

    @NotNull(message = "La longitude est obligatoire", groups = Create.class)
    @DecimalMin("-180") @DecimalMax("180")
    private Double longitude;

    private TypeDechet typeDechet;

    @Min(0) @Max(100)
    private Integer niveauRemplissage;

    private Boolean actif;
}
