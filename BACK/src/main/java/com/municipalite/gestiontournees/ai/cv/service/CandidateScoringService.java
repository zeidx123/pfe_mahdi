package com.municipalite.gestiontournees.ai.cv.service;

import com.municipalite.gestiontournees.ai.cv.model.*;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class CandidateScoringService {
    public void evaluate(Candidate candidate, DriverRequirements profile) {
        Map<String, FieldEvidence> fields = candidate.getFieldEvidence() == null ? Map.of() : candidate.getFieldEvidence();
        var criteria = new ArrayList<CriterionScore>();
        FieldEvidence licence = fields.get("typePermis");
        boolean accepted = licence != null && licence.getValue() != null
                && Arrays.stream(licence.getValue().toUpperCase(Locale.ROOT).split("[^A-Z0-9]+"))
                .anyMatch(profile.getAcceptedLicences()::contains);
        criteria.add(criterion("typePermis", "Permis " + String.join(" / ", profile.getAcceptedLicences()), licence,
                profile.isLicenceRequired(), accepted, accepted ? profile.getLicenceWeight() : 0,
                profile.getLicenceWeight(), "Catégorie de permis confirmée par le superviseur."));
        FieldEvidence experience = fields.get("anneesExperience");
        double years = number(experience);
        boolean targetMet = years >= profile.getExperienceTargetYears();
        double experiencePoints = profile.isExperienceRequired() && !targetMet ? 0
                : profile.getExperienceWeight() * Math.min(years / profile.getExperienceTargetYears(), 1);
        criteria.add(criterion("anneesExperience", "Expérience de conduite", experience,
                profile.isExperienceRequired(), targetMet, experiencePoints, profile.getExperienceWeight(),
                profile.isExperienceRequired() ? "Seuil obligatoire : " + profile.getExperienceTargetYears() + " ans. Aucun point sous ce seuil."
                        : "Cible de " + profile.getExperienceTargetYears() + " ans ; points proportionnels, sans exclusion sous la cible."));
        FieldEvidence safety = fields.get("formationSecurite");
        boolean safetyMet = safety != null && "true".equals(safety.getValue());
        criteria.add(criterion("formationSecurite", "Formation sécurité pertinente", safety, profile.isSafetyRequired(),
                safetyMet, safetyMet ? profile.getSafetyWeight() : 0, profile.getSafetyWeight(), "Formation pertinente confirmée."));
        FieldEvidence collection = fields.get("experienceCollecteConfirmee");
        boolean collectionMet = collection != null && "true".equals(collection.getValue());
        criteria.add(criterion("experienceCollecteConfirmee", "Expérience de collecte", collection, profile.isCollectionRequired(),
                collectionMet, collectionMet ? profile.getCollectionWeight() : 0, profile.getCollectionWeight(), "Expérience de collecte confirmée."));
        boolean provisional = criteria.stream().anyMatch(c -> (c.getMaxPoints() > 0 || c.isRequired()) && !"VERIFIED".equals(c.getEvidenceStatus()));
        String required = criteria.stream().anyMatch(c -> "UNMET".equals(c.getRequiredStatus())) ? "UNMET"
                : criteria.stream().anyMatch(c -> "NEEDS_REVIEW".equals(c.getRequiredStatus())) ? "NEEDS_REVIEW" : "MET";
        int score = (int) Math.round(criteria.stream().mapToDouble(CriterionScore::getPoints).sum());
        candidate.setCriterionScores(criteria);
        candidate.setScoreCompatibilite(score);
        candidate.setRequiredCriteriaStatus(required);
        candidate.setScoreProvisional(provisional);
        candidate.setRequirementsVersion(profile.getVersion());
        candidate.setRequirementsSnapshot(profile);
        candidate.setRecommandation(provisional ? "A_EXAMINER" : "UNMET".equals(required) ? "PEU_COMPATIBLE" : score >= 70 ? "RECOMMANDE" : "A_EXAMINER");
        candidate.setDetailScore("Points confirmés : " + score + "/100. Critères requis : " + required + ". Décision finale humaine.");
        if (candidate.getEvaluations() == null) candidate.setEvaluations(new ArrayList<>());
        candidate.getEvaluations().add(CandidateEvaluation.builder().requirements(profile)
                .evaluatedFields(new LinkedHashMap<>(fields)).criteria(criteria).score(score)
                .requiredCriteriaStatus(required).provisional(provisional).evaluatedAt(LocalDateTime.now()).build());
    }

    private CriterionScore criterion(String key, String label, FieldEvidence evidence, boolean required, boolean met,
                                     double points, int maxPoints, String explanation) {
        boolean verified = evidence != null && "VERIFIED".equals(evidence.getStatus()) && evidence.getValue() != null;
        return CriterionScore.builder().key(key).label(label).value(evidence == null ? null : evidence.getValue())
                .required(required).requiredStatus(!required ? "NOT_REQUIRED" : !verified ? "NEEDS_REVIEW" : met ? "MET" : "UNMET")
                .evidenceStatus(evidence == null ? "MISSING" : evidence.getStatus())
                .points(verified ? Math.round(Math.max(points, 0) * 100.0) / 100.0 : 0).maxPoints(maxPoints)
                .explanation(verified ? explanation : "Information non vérifiée ; aucun point confirmé. " + explanation).build();
    }

    private double number(FieldEvidence field) {
        try { double value = Double.parseDouble(field.getValue()); return Double.isFinite(value) && value >= 0 && value <= 60 ? value : 0; }
        catch (Exception ignored) { return 0; }
    }
}
