package com.municipalite.gestiontournees.pointcollecte.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "points_collecte")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointCollecte {

    @Id
    private String id;

    @Indexed(unique = true)
    private String reference; // ex: B-001

    private String adresse;
    private String zone;
    private Double latitude;
    private Double longitude;
    private TypeDechet typeDechet;

    // Remplissage du bac (0-100%), alimenté par l'ESP32
    @Builder.Default
    private Integer niveauRemplissage = 0;

    private boolean actif;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
