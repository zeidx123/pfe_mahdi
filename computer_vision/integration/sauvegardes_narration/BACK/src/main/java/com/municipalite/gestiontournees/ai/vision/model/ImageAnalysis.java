package com.municipalite.gestiontournees.ai.vision.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Image evidence assessment with provider metadata and separate human review.
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
    // Sanitized diagnostics only; never persist provider bodies, credentials or image payloads here.
    private String failureCategory;
    private Integer providerHttpStatus;
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

    // Moteur LOCAL uniquement (null pour les analyses LLM). confidence reste null dans les deux moteurs.
    private Map<String, Double> probabilities; // CANNOT_DETERMINE, DEBORDEMENT, PROPRE
    private String predictedClass;   // sortie brute du modèle, avant correspondance avec status
    private String qualityIssue;     // TROP_SOMBRE, SUREXPOSEE, FLOUE ou null
    private Double sharpness;
    private Double meanBrightness;
    private String decision;         // CONFIRMATION_AUTO ou A_VERIFIER : ordonne la revue humaine, rien d'autre
    private String modelSha256;

    // Validation chauffeur
    private Boolean valideeParChauffeur;
    private String correctionChauffeur;

    private LocalDateTime createdAt;
}
