package com.municipalite.gestiontournees.ai.cv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.municipalite.gestiontournees.ai.common.AiServiceException;
import com.municipalite.gestiontournees.ai.common.CollectionAiClient;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.cv.dto.CandidateReviewRequest;
import com.municipalite.gestiontournees.ai.cv.model.*;
import com.municipalite.gestiontournees.ai.cv.repository.CandidateRepository;
import com.municipalite.gestiontournees.ai.cv.service.*;
import com.municipalite.gestiontournees.security.AccessService;
import com.municipalite.gestiontournees.user.model.User;
import com.municipalite.gestiontournees.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;

import static com.municipalite.gestiontournees.ai.cv.CandidateScoringTest.field;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CandidateReviewTest {
    @TempDir Path directory;
    private final CvExtractionService extraction = mock(CvExtractionService.class);
    private final CollectionAiClient qwen = mock(CollectionAiClient.class);
    private final CandidateRepository candidates = mock(CandidateRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final DriverRequirementsService requirements = mock(DriverRequirementsService.class);
    private final AccessService access = mock(AccessService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private CvAnalysisService service;

    @BeforeEach void setup() {
        var properties = new AiProperties(); properties.getStorage().setCvDir(directory.toString());
        properties.setCollectionProvider(AiProperties.CollectionProvider.OPENROUTER);
        properties.getGemini().setModelText("chatbot-model-only");
        properties.getOpenRouter().setModelText("qwen/qwen3.8-27b:free");
        service = new CvAnalysisService(extraction, qwen, properties, candidates, users, encoder,
                new ObjectMapper().registerModule(new JavaTimeModule()), requirements, new CandidateScoringService(), access);
        when(requirements.current()).thenReturn(DriverRequirements.initial());
        when(access.currentUser()).thenReturn(User.builder().id("supervisor").build());
        when(candidates.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void providerFailureDoesNotTurnTelephoneIntoCinOrRejectOlderApplicant() {
        String text = "Nom : Exemple\nPrénom : Test\nTéléphone : 22123456\n55 ans\nPermis : C\n1.5 ans de conduite";
        when(extraction.extract(any())).thenReturn(new CvExtractionService.Extraction(
                List.of(new CvExtractionService.TextPart(1, "Page 1", text)), "EXTRACTED"));
        when(qwen.generateJson(any(), anyMap())).thenThrow(new AiServiceException("unavailable"));
        var saved = service.analyzeAndSaveCv(new MockMultipartFile("cv", "synthetic.pdf", "application/pdf", new byte[]{1}));
        assertNull(saved.getCin()); assertEquals("22123456", saved.getTelephone());
        assertEquals(1.5, saved.getAnneesExperience()); assertEquals("EN_ATTENTE", saved.getStatut());
        assertEquals("PROVIDER_UNAVAILABLE", saved.getExtractionStatus());
        assertEquals("OPENROUTER", saved.getExtractionProvider());
        assertEquals("qwen/qwen3.8-27b:free", saved.getExtractionModel());
        assertEquals("UNCERTAIN", saved.getFieldEvidence().get("typePermis").getStatus());
        assertEquals(0, saved.getScoreCompatibilite());
        verifyNoInteractions(users);
    }

    @Test void sourceQuotesMustExistAndNeverAutoVerifyField() {
        when(extraction.extract(any())).thenReturn(new CvExtractionService.Extraction(
                List.of(new CvExtractionService.TextPart(2, "Page 2", "Permis : C")), "EXTRACTED"));
        when(qwen.generateJson(any(), anyMap())).thenReturn("{\"fields\":{\"typePermis\":{\"value\":\"C\",\"snippet\":\"Permis : C\"},\"nom\":{\"value\":\"Inventé\",\"snippet\":\"citation absente\"}}}");
        var saved = service.analyzeAndSaveCv(new MockMultipartFile("cv", "synthetic.pdf", "application/pdf", new byte[]{1}));
        assertEquals(2, saved.getFieldEvidence().get("typePermis").getPageNumber());
        assertNull(saved.getFieldEvidence().get("nom").getSnippet());
        assertEquals("UNCERTAIN", saved.getFieldEvidence().get("nom").getStatus());
        assertEquals("NEEDS_REVIEW", saved.getRequiredCriteriaStatus());
        assertEquals("OPENROUTER", saved.getExtractionProvider());
        assertEquals("qwen/qwen3.8-27b:free", saved.getExtractionModel());
    }

    @Test void selfCheckDowngradesAFieldWhoseCitedSnippetDoesNotSupportItsValue() {
        when(extraction.extract(any())).thenReturn(new CvExtractionService.Extraction(
                List.of(new CvExtractionService.TextPart(1, "Page 1", "Permis : C")), "EXTRACTED"));
        when(qwen.generateJson(any(), anyMap()))
                .thenReturn("{\"fields\":{\"typePermis\":{\"value\":\"C\",\"snippet\":\"Permis : C\"}}}")
                .thenReturn("{\"confirmations\":{\"typePermis\":false}}");
        var saved = service.analyzeAndSaveCv(new MockMultipartFile("cv", "synthetic.pdf", "application/pdf", new byte[]{1}));
        var field = saved.getFieldEvidence().get("typePermis");
        assertEquals("INVALID", field.getStatus());
        assertEquals("C", field.getValue());
        assertNotNull(field.getReviewNote());
        assertEquals("EXTRACTED", saved.getExtractionStatus());
        verify(qwen, times(2)).generateJson(any(), anyMap());
    }

    @Test void selfCheckFailureDoesNotFailExtractionAndLeavesFieldUncertain() {
        when(extraction.extract(any())).thenReturn(new CvExtractionService.Extraction(
                List.of(new CvExtractionService.TextPart(1, "Page 1", "Permis : C")), "EXTRACTED"));
        when(qwen.generateJson(any(), anyMap()))
                .thenReturn("{\"fields\":{\"typePermis\":{\"value\":\"C\",\"snippet\":\"Permis : C\"}}}")
                .thenThrow(new AiServiceException("self-check unavailable"));
        var saved = service.analyzeAndSaveCv(new MockMultipartFile("cv", "synthetic.pdf", "application/pdf", new byte[]{1}));
        assertEquals("UNCERTAIN", saved.getFieldEvidence().get("typePermis").getStatus());
        assertEquals("EXTRACTED", saved.getExtractionStatus());
    }

    @Test void httpFailureAndInvalidExtractionHaveDistinctSanitizedDiagnostics() {
        when(extraction.extract(any())).thenReturn(new CvExtractionService.Extraction(
                List.of(new CvExtractionService.TextPart(1, "Page 1", "Permis : C")), "EXTRACTED"));
        when(qwen.generateJson(any(), anyMap())).thenThrow(new AiServiceException("private-provider-response", "PROVIDER_HTTP_ERROR", 503, null));
        var unavailable = service.analyzeAndSaveCv(new MockMultipartFile("cv", "synthetic.pdf", "application/pdf", new byte[]{1}));
        assertEquals("PROVIDER_UNAVAILABLE", unavailable.getExtractionStatus());
        assertEquals("PROVIDER_HTTP_ERROR", unavailable.getExtractionFailureCategory());
        assertEquals(503, unavailable.getExtractionHttpStatus());
        assertFalse(unavailable.getExtractionError().contains("private-provider-response"));

        reset(qwen);
        when(qwen.generateJson(any(), anyMap())).thenReturn("{\"privateInvalidShape\":true}");
        var invalid = service.analyzeAndSaveCv(new MockMultipartFile("cv", "synthetic.pdf", "application/pdf", new byte[]{1}));
        assertEquals("INVALID_RESPONSE", invalid.getExtractionStatus());
        assertEquals("INVALID_EXTRACTION_RESPONSE", invalid.getExtractionFailureCategory());
        assertNull(invalid.getExtractionHttpStatus());
        assertFalse(invalid.getExtractionError().contains("privateInvalidShape"));
        assertEquals("LOCAL", invalid.getFieldEvidence().get("typePermis").getSource());
    }

    @Test void retryKeepsStoredSourceIdentityDecisionAndEveryReviewedField() throws Exception {
        var document = new CvExtractionService.Extraction(List.of(new CvExtractionService.TextPart(1, "Page 1", "Permis : B")), "EXTRACTED");
        when(extraction.extract(any())).thenReturn(document);
        when(extraction.extractStored(any())).thenReturn(document);
        when(qwen.generateJson(any(), anyMap())).thenThrow(new AiServiceException("private-response", "PROVIDER_HTTP_ERROR", 503, null));
        var candidate = service.analyzeAndSaveCv(new MockMultipartFile("cv", "synthetic.pdf", "application/pdf", new byte[]{1}));
        candidate.setId("retry-candidate");
        candidate.setStatut("EN_ENTRETIEN");
        candidate.setNotesSuperviseur("Décision humaine conservée");
        var reviewed = FieldEvidence.builder().value("C").status("VERIFIED").source("MANUAL").reviewedBy("supervisor")
                .reviewedAt(LocalDateTime.now()).reviewNote("Vérifié sur le justificatif").build();
        var missing = FieldEvidence.builder().status("MISSING").source("MANUAL").reviewedBy("supervisor").build();
        candidate.getFieldEvidence().put("typePermis", reviewed);
        candidate.getFieldEvidence().put("email", missing);
        String sourcePath = candidate.getCvFilePath();
        LocalDateTime originalDeposit = candidate.getDateDepot();
        when(candidates.findById("retry-candidate")).thenReturn(Optional.of(candidate));
        reset(qwen);
        when(qwen.generateJson(any(), anyMap())).thenReturn("{\"fields\":{\"typePermis\":{\"value\":\"B\",\"snippet\":\"Permis : B\"},\"email\":{\"value\":\"proposal@example.test\"}}}");

        var result = service.reanalyze("retry-candidate");
        assertEquals("retry-candidate", result.getId());
        assertEquals(sourcePath, result.getCvFilePath());
        assertEquals(originalDeposit, result.getDateDepot());
        assertEquals("EN_ENTRETIEN", result.getStatut());
        assertEquals("Décision humaine conservée", result.getNotesSuperviseur());
        assertSame(reviewed, result.getFieldEvidence().get("typePermis"));
        assertSame(missing, result.getFieldEvidence().get("email"));
        assertEquals("EXTRACTED", result.getExtractionStatus());
        assertNull(result.getExtractionFailureCategory());
        assertNull(result.getExtractionHttpStatus());
        assertNull(result.getExtractionError());
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
        verifyNoInteractions(users);
    }

    @Test void retryRejectsLinkedDriverAndUnsafeStoredSource() {
        var candidate = new Candidate(); candidate.setRequirementsVersion(1); candidate.setAgentId("linked-driver");
        when(candidates.findById("cv1")).thenReturn(Optional.of(candidate));
        assertThrows(IllegalArgumentException.class, () -> service.reanalyze("cv1"));
        candidate.setAgentId(null); candidate.setCvFilePath("/uploads/cv/../outside.pdf");
        assertThrows(IllegalArgumentException.class, () -> service.reanalyze("cv1"));
        verifyNoInteractions(extraction, qwen);
        verify(candidates, never()).save(any());
    }

    @Test void acceptanceRequiresVerifiedRealFieldsAndRepeatedAcceptanceCreatesOneAccount() {
        var candidate = CandidateScoringTest.fixture("C", "VERIFIED", 0.5, "VERIFIED", false, false);
        candidate.setId("cv1"); new CandidateScoringService().evaluate(candidate, DriverRequirements.initial());
        when(candidates.findById("cv1")).thenReturn(Optional.of(candidate));
        assertThrows(IllegalArgumentException.class, () -> service.updateDecision("cv1", "ACCEPTE", "Decision"));
        verify(users, never()).save(any());
        Map<String, FieldEvidence> values = new LinkedHashMap<>(candidate.getFieldEvidence());
        Map.of("nom", "Exemple", "prenom", "Test", "cin", "12345678", "email", "synthetic@example.invalid",
                "telephone", "22123456", "numeroPermis", "PERMIS001", "dateExpirationPermis", "2035-01-01")
                .forEach((key, value) -> values.put(key, field(value, "VERIFIED")));
        var review = new CandidateReviewRequest(); review.setFields(values); service.review("cv1", review);
        when(encoder.encode("12345678")).thenReturn("test-only-encoded");
        when(users.save(any())).thenAnswer(invocation -> { User agent = invocation.getArgument(0); agent.setId("driver1"); return agent; });
        service.updateDecision("cv1", "ACCEPTE", "Decision humaine");
        service.updateDecision("cv1", "ACCEPTE", "Notes mises à jour");
        assertEquals("driver1", candidate.getAgentId());
        assertEquals("ACCEPTE", candidate.getStatut());
        verify(users, times(1)).save(any());
    }

    @Test void missingReviewStatusIsRejectedAsValidationError() {
        var candidate = CandidateScoringTest.fixture("C", "VERIFIED", 1, "VERIFIED", false, false);
        candidate.setRequirementsVersion(1);
        when(candidates.findById("cv1")).thenReturn(Optional.of(candidate));
        var request = new CandidateReviewRequest();
        request.setFields(Map.of("typePermis", field("C", null)));
        assertThrows(IllegalArgumentException.class, () -> service.review("cv1", request));
        verify(candidates, never()).save(any());
    }

    @Test void contradictoryNoLicenceAndCategoryCannotBeVerified() {
        var candidate = CandidateScoringTest.fixture("C", "VERIFIED", 1, "VERIFIED", false, false);
        candidate.setRequirementsVersion(1);
        when(candidates.findById("cv1")).thenReturn(Optional.of(candidate));
        var request = new CandidateReviewRequest();
        request.setFields(Map.of("typePermis", field("AUCUN,C", "VERIFIED")));
        assertThrows(IllegalArgumentException.class, () -> service.review("cv1", request));
        verify(candidates, never()).save(any());
        assertEquals("C", candidate.getFieldEvidence().get("typePermis").getValue());

        request.setFields(Map.of("typePermis", field("AUCUN", "VERIFIED")));
        var reviewed = service.review("cv1", request);
        assertEquals("UNMET", reviewed.getRequiredCriteriaStatus());
        assertEquals(0, reviewed.getCriterionScores().get(0).getPoints());
    }

    @Test void invalidExperienceCannotBeMarkedVerified() {
        var candidate = CandidateScoringTest.fixture("C", "VERIFIED", 1, "VERIFIED", false, false);
        candidate.setRequirementsVersion(1);
        when(candidates.findById("cv1")).thenReturn(Optional.of(candidate));
        var request = new CandidateReviewRequest(); request.setFields(Map.of("anneesExperience", field("NaN", "VERIFIED")));
        assertThrows(IllegalArgumentException.class, () -> service.review("cv1", request));
        verify(candidates, never()).save(any());
    }

    @Test void reviewReportsEveryInvalidFieldInOneErrorInsteadOfOneAtATime() {
        var candidate = CandidateScoringTest.fixture("C", "VERIFIED", 1, "VERIFIED", false, false);
        candidate.setRequirementsVersion(1);
        when(candidates.findById("cv1")).thenReturn(Optional.of(candidate));
        var request = new CandidateReviewRequest();
        request.setFields(Map.of("anneesExperience", field("NaN", "VERIFIED"), "email", field("not-an-email", "VERIFIED")));
        var error = assertThrows(IllegalArgumentException.class, () -> service.review("cv1", request));
        assertTrue(error.getMessage().contains("anneesExperience"));
        assertTrue(error.getMessage().contains("email"));
        verify(candidates, never()).save(any());
    }

    @Test void acceptanceReportsEveryUnverifiedIdentityFieldInOneErrorInsteadOfOneAtATime() {
        var candidate = CandidateScoringTest.fixture("C", "VERIFIED", 0.5, "VERIFIED", false, false);
        candidate.setId("cv1"); new CandidateScoringService().evaluate(candidate, DriverRequirements.initial());
        when(candidates.findById("cv1")).thenReturn(Optional.of(candidate));
        Map<String, FieldEvidence> values = new LinkedHashMap<>(candidate.getFieldEvidence());
        Map.of("nom", "Exemple", "prenom", "Test", "numeroPermis", "PERMIS001", "dateExpirationPermis", "2035-01-01")
                .forEach((key, value) -> values.put(key, field(value, "VERIFIED")));
        var review = new CandidateReviewRequest(); review.setFields(values); service.review("cv1", review);
        var error = assertThrows(IllegalArgumentException.class, () -> service.updateDecision("cv1", "ACCEPTE", "Decision"));
        assertTrue(error.getMessage().contains("cin"));
        assertTrue(error.getMessage().contains("email"));
        assertTrue(error.getMessage().contains("telephone"));
        assertFalse(error.getMessage().contains("nom,") && error.getMessage().contains("prenom,"));
        verify(users, never()).save(any());
    }
}
