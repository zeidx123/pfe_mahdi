package com.municipalite.gestiontournees.ai.vision.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Entité représentant une analyse d'image de point de collecte par Gemini Vision.
 */
@Document(collection = "image_analyses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageAnalysis {

    @Id
    private String id;

    private String pointCollecteId;
    private String tourneeId;
    private String chauffeurId;
    private String photoUrl; // Legacy alias for AFTER evidence
    private String afterPhotoUrl;
    private String beforePhotoUrl;
    private String inputMode; // AFTER_ONLY or BEFORE_AFTER
    private String provider;
    private String model;
    private String promptVersion;
    private String reviewedLabel;
    private String reviewNotes;
    private String reviewedBy;
    private LocalDateTime reviewedAt;

    private String status; // Original prediction; never overwritten by human correction
    private String typeAnomalie;     // CONTENEUR_NORMAL, CONTENEUR_PLEIN, CONTENEUR_DEBORDE, CONTENEUR_RENVERSE, CONTENEUR_ENDOMMAGE, DECHETS_AUTOUR, POINT_INACCESSIBLE, OBSTACLE, AUTRE_ANOMALIE
    private Double confidence;       // 0.0 à 1.0
    private String description;
    private String severity;         // INFO, WARNING, CRITICAL

    private boolean problemCreated;
    private String problemId;

    // Validation chauffeur
    private Boolean valideeParChauffeur;
    private String correctionChauffeur;

    private LocalDateTime createdAt;
}
