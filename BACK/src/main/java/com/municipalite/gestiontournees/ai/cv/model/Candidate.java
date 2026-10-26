package com.municipalite.gestiontournees.ai.cv.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Entité représentant un candidat dont le CV a été soumis pour analyse IA.
 */
@Document(collection = "candidates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candidate {

    @Id
    private String id;

    // Informations extraites du CV
    private String cin;
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private String adresse;
    private String dateNaissance;
    private Integer age;
    private String numeroPermis;
    private String typePermis;
    private Double anneesExperience;
    private String experienceChauffeur;   // Résumé des expériences comme chauffeur
    private String experienceCollecte;    // Expérience dans la collecte de déchets
    private String formations;
    private String competences;
    private String certifications;
    private String disponibilite;
    private String langues;
    private String autresInformations;

    // Chemin du fichier CV stocké
    private String cvFilePath;
    private String cvFileName;

    // Résultat de l'analyse IA
    private Integer scoreCompatibilite;   // 0 à 100
    private String recommandation;        // RECOMMANDE, A_EXAMINER, PEU_COMPATIBLE
    private String detailScore;           // Explication détaillée du score
    private String analyseIa;            // Résumé IA complet

    // Décision du superviseur
    private String statut;               // EN_ATTENTE, ACCEPTE, REJETE, EN_ENTRETIEN
    private String notesSuperviseur;

    private Integer legacyScoreCompatibilite;
    private String legacyDetailScore;
    private String legacyRecommandation;
    private String agentId;
    private String agentMatricule;
    private String extractionStatus; // EXTRACTED, PROVIDER_UNAVAILABLE, INVALID_RESPONSE, MANUAL_REVIEW_REQUIRED, LEGACY_REVIEW_REQUIRED
    private String extractionFailureCategory;
    private Integer extractionHttpStatus;
    private String extractionError; // Sanitized explanation only; no provider body or CV content.
    private String extractionModel;
    private String extractionProvider;
    private String extractionPromptVersion;
    private Boolean formationSecurite;
    private Boolean experienceCollecteConfirmee;
    private String dateExpirationPermis;
    private java.util.Map<String, FieldEvidence> fieldEvidence;
    private java.util.List<CriterionScore> criterionScores;
    private String requiredCriteriaStatus;
    private Integer requirementsVersion;
    private DriverRequirements requirementsSnapshot;
    private boolean scoreProvisional;
    private java.util.List<CandidateEvaluation> evaluations;

    private LocalDateTime dateDepot;
    private LocalDateTime dateMiseAJour;
}
