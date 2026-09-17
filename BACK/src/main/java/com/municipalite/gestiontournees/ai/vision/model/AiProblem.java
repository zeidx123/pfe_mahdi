package com.municipalite.gestiontournees.ai.vision.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Problème signalé/détecté automatiquement par l'IA lors de la collecte.
 */
@Document(collection = "ai_problems")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiProblem {

    @Id
    private String id;

    private String imageAnalysisId;
    private String tourneeId;
    private String pointCollecteId;
    private String chauffeurId;
    private String camionId;

    private String photoUrl;
    private Double latitude;
    private Double longitude;

    private String typeProbleme;
    private String description;
    private Double scoreConfiance;
    private String severite;

    private String statut; // SIGNALEE, EN_COURS, RESOLUE, REJETEE
    private String remedeOuNote;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
