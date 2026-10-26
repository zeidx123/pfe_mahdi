package com.municipalite.gestiontournees.ai.cv;

import com.municipalite.gestiontournees.ai.cv.model.*;
import com.municipalite.gestiontournees.ai.cv.service.CandidateScoringService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class CandidateScoringTest {
    private final CandidateScoringService scoring = new CandidateScoringService();

    @ParameterizedTest(name = "{0}")
    @CsvFileSource(resources = "/evaluation/candidate-rubric-seed.csv", numLinesToSkip = 1)
    void rubricSeed(String name, String licence, String licenceState, double years, String experienceState,
                    boolean safety, boolean collection, int expectedScore, String expectedRequiredStatus) {
        var candidate = fixture(licence, licenceState, years, experienceState, safety, collection);
        scoring.evaluate(candidate, DriverRequirements.initial());
        assertEquals(expectedScore, candidate.getScoreCompatibilite());
        assertEquals(expectedRequiredStatus, candidate.getRequiredCriteriaStatus());
        assertEquals(candidate.getScoreCompatibilite(), (int) Math.round(candidate.getCriterionScores().stream().mapToDouble(CriterionScore::getPoints).sum()));
        assertEquals("EN_ATTENTE", candidate.getStatut());
    }

    @Test void identityAndAgeNeverChangeSuitabilityPoints() {
        var candidate = fixture("C", "VERIFIED", 1.5, "VERIFIED", false, false);
        scoring.evaluate(candidate, DriverRequirements.initial());
        int original = candidate.getScoreCompatibilite();
        candidate.setCin("12345678"); candidate.setDateNaissance("1960-01-01"); candidate.setTelephone("22123456");
        scoring.evaluate(candidate, DriverRequirements.initial());
        assertEquals(original, candidate.getScoreCompatibilite());
    }

    @Test void explicitMandatoryExperienceReceivesNoPartialPointsBelowThreshold() {
        var candidate = fixture("C", "VERIFIED", 1, "VERIFIED", false, false);
        var profile = DriverRequirements.initial(); profile.setExperienceRequired(true);
        scoring.evaluate(candidate, profile);
        assertEquals("UNMET", candidate.getRequiredCriteriaStatus());
        assertEquals(0, candidate.getCriterionScores().get(1).getPoints());
    }

    @Test void missingOptionalFieldsSettleTheScoreInsteadOfStayingProvisionalForever() {
        // A CV that never mentions safety training or collection experience leaves those two
        // MISSING forever; that must not block the recommendation, since there is no pending
        // AI guess to confirm there (unlike UNCERTAIN, where a proposal is still unreviewed).
        var candidate = new Candidate(); candidate.setStatut("EN_ATTENTE");
        candidate.setAge(26);
        var fields = new LinkedHashMap<String, FieldEvidence>();
        fields.put("typePermis", field("C", "VERIFIED"));
        fields.put("anneesExperience", field("3", "VERIFIED"));
        fields.put("formationSecurite", FieldEvidence.builder().status("MISSING").build());
        fields.put("experienceCollecteConfirmee", FieldEvidence.builder().status("MISSING").build());
        candidate.setFieldEvidence(fields);
        scoring.evaluate(candidate, DriverRequirements.initial());
        assertFalse(candidate.isScoreProvisional());
        assertEquals(80, candidate.getScoreCompatibilite());
        assertEquals("RECOMMANDE", candidate.getRecommandation());
    }

    @Test void ageBetween20And35IsRecommendedWhenScoreAtLeast70() {
        var candidate = fixture("C", "VERIFIED", 3, "VERIFIED", true, true);
        candidate.setAge(25);
        scoring.evaluate(candidate, DriverRequirements.initial());
        assertTrue(candidate.getScoreCompatibilite() >= 70);
        assertEquals("RECOMMANDE", candidate.getRecommandation());
    }

    @Test void ageBoundaryChecks() {
        // 20 ans: admissible
        var c20 = fixture("C", "VERIFIED", 3, "VERIFIED", true, true);
        c20.setAge(20);
        scoring.evaluate(c20, DriverRequirements.initial());
        assertEquals("RECOMMANDE", c20.getRecommandation());

        // 19 ans: non admissible (< 20 ans)
        var c19 = fixture("C", "VERIFIED", 3, "VERIFIED", true, true);
        c19.setAge(19);
        scoring.evaluate(c19, DriverRequirements.initial());
        assertEquals("PEU_COMPATIBLE", c19.getRecommandation());

        // 34 ans: admissible (< 35 ans)
        var c34 = fixture("C", "VERIFIED", 3, "VERIFIED", true, true);
        c34.setAge(34);
        scoring.evaluate(c34, DriverRequirements.initial());
        assertEquals("RECOMMANDE", c34.getRecommandation());

        // 35 ans: hors critère (inférieur strict à 35 ans)
        var c35 = fixture("C", "VERIFIED", 3, "VERIFIED", true, true);
        c35.setAge(35);
        scoring.evaluate(c35, DriverRequirements.initial());
        assertEquals("PEU_COMPATIBLE", c35.getRecommandation());

        // Âge inconnu : reste à examiner
        var cUnknown = fixture("C", "VERIFIED", 3, "VERIFIED", true, true);
        scoring.evaluate(cUnknown, DriverRequirements.initial());
        assertEquals("A_EXAMINER", cUnknown.getRecommandation());
    }

    @Test void unreviewedAiProposalKeepsTheScoreProvisional() {
        var candidate = fixture("C", "UNCERTAIN", 3, "VERIFIED", true, true);
        scoring.evaluate(candidate, DriverRequirements.initial());
        assertTrue(candidate.isScoreProvisional());
        assertEquals("A_EXAMINER", candidate.getRecommandation());
    }

    @Test void newEvaluationPreservesOldProfileAndReviewedValues() {
        var candidate = fixture("C", "VERIFIED", 1, "VERIFIED", false, false);
        scoring.evaluate(candidate, DriverRequirements.initial());
        var next = DriverRequirements.initial(); next.setVersion(2); next.setExperienceTargetYears(4);
        candidate.getFieldEvidence().put("anneesExperience", field("2", "VERIFIED"));
        scoring.evaluate(candidate, next);
        assertEquals(2, candidate.getEvaluations().size());
        assertEquals(1, candidate.getEvaluations().get(0).getRequirements().getVersion());
        assertEquals("1.0", candidate.getEvaluations().get(0).getEvaluatedFields().get("anneesExperience").getValue());
        assertEquals(2, candidate.getRequirementsVersion());
    }

    static Candidate fixture(String licence, String licenceState, double years, String experienceState, boolean safety, boolean collection) {
        var candidate = new Candidate(); candidate.setStatut("EN_ATTENTE");
        var fields = new LinkedHashMap<String, FieldEvidence>();
        fields.put("typePermis", field(licence, licenceState));
        fields.put("anneesExperience", field(Double.toString(years), experienceState));
        fields.put("formationSecurite", field(Boolean.toString(safety), "VERIFIED"));
        fields.put("experienceCollecteConfirmee", field(Boolean.toString(collection), "VERIFIED"));
        candidate.setFieldEvidence(fields);
        return candidate;
    }
    static FieldEvidence field(String value, String status) { return FieldEvidence.builder().value(value).status(status).build(); }
}
