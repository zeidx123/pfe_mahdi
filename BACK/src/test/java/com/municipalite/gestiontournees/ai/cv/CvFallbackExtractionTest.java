package com.municipalite.gestiontournees.ai.cv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.municipalite.gestiontournees.ai.common.*;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.cv.model.*;
import com.municipalite.gestiontournees.ai.cv.repository.CandidateRepository;
import com.municipalite.gestiontournees.ai.cv.service.*;
import com.municipalite.gestiontournees.security.AccessService;
import com.municipalite.gestiontournees.user.repository.UserRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Authored development documents + mocked outage; no provider, MongoDB or mail. */
class CvFallbackExtractionTest {
    @TempDir Path storage;
    private CvAnalysisService service;
    private UserRepository users;
    @BeforeEach void setup() {
        var properties = new AiProperties();
        properties.getStorage().setCvDir(storage.toString());
        properties.getGemini().setModelText("fixture-configured-model");
        var gemini = mock(GeminiClient.class);
        when(gemini.generateJson(any())).thenThrow(new AiServiceException("simulated provider outage"));
        var candidates = mock(CandidateRepository.class);
        when(candidates.save(any())).thenAnswer(call -> call.getArgument(0));
        var requirements = mock(DriverRequirementsService.class);
        when(requirements.current()).thenReturn(DriverRequirements.initial());
        users = mock(UserRepository.class);
        service = new CvAnalysisService(new CvExtractionService(), gemini, properties, candidates, users,
                mock(PasswordEncoder.class), new ObjectMapper().registerModule(new JavaTimeModule()),
                requirements, new CandidateScoringService(), mock(AccessService.class));
    }
    @ParameterizedTest @CsvSource({"fictional-driver-c.docx,C,1.0,true", "fictional-driver-b.docx,B,0.5,false"})
    void actualAuthoredDocxKeepsExplicitValuesDuringOutage(String filename, String licence, String years, String safety) throws Exception {
        Path file = Path.of(System.getProperty("basedir", "."), "..", "scripts", "evaluation", "fixtures", "cv-development", filename);
        var result = service.analyzeAndSaveCv(new MockMultipartFile("cv", filename,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", Files.readAllBytes(file)));
        assertAll(() -> evidence(result, "typePermis", licence, "UNCERTAIN"),
                () -> evidence(result, "anneesExperience", years, "UNCERTAIN"),
                () -> evidence(result, "formationSecurite", safety, "UNCERTAIN"),
                () -> evidence(result, "experienceCollecteConfirmee", "false", "UNCERTAIN"),
                () -> evidence(result, "cin", null, "MISSING"),
                () -> evidence(result, "telephone", licence.equals("B") ? "123" : null, licence.equals("B") ? "INVALID" : "MISSING"),
                () -> evidence(result, "nom", licence.equals("B") ? "Exemple Beta" : "Exemple Alpha", "UNCERTAIN"),
                () -> evidence(result, "prenom", "Fictif", "UNCERTAIN"),
                () -> assertEquals("fixture-configured-model", result.getExtractionModel()),
                () -> assertEquals("PROVIDER_UNAVAILABLE", result.getExtractionStatus()),
                () -> assertEquals(0, result.getScoreCompatibilite()));
        assertEquals("LOCAL", result.getFieldEvidence().get("typePermis").getSource());
        assertNotNull(result.getFieldEvidence().get("typePermis").getLocation());
        assertTrue(result.getFieldEvidence().values().stream().noneMatch(e -> "VERIFIED".equals(e.getStatus())));
        verifyNoInteractions(users);
    }
    @Test void blankLabelsNeverConsumeTheFollowingLineAndAbsenceIsNotInvalid() throws Exception {
        var r = document("Nom :\nPrénom : Test\nCIN :\nTéléphone : 22123456\nPermis : non fourni.\nExpérience de conduite : non renseignée.");
        assertAll(() -> evidence(r,"nom",null,"MISSING"), () -> evidence(r,"prenom","Test","UNCERTAIN"),
                () -> evidence(r,"cin",null,"MISSING"), () -> evidence(r,"telephone","22123456","UNCERTAIN"),
                () -> evidence(r,"typePermis",null,"MISSING"), () -> evidence(r,"anneesExperience",null,"MISSING"));
    }
    @Test void ambiguousEvidenceRemainsUnresolvedAndUnlabelledNumbersAreNotIdentityOrExperience() throws Exception {
        var r = document("55 ans\nNuméro de référence : 12345678\nExpérience de conduite : plusieurs années, à confirmer.\nFormation de sécurité : à confirmer.\nExpérience de collecte des déchets : non précisée.");
        assertAll(() -> evidence(r,"cin",null,"MISSING"), () -> evidence(r,"telephone",null,"MISSING"),
                () -> evidence(r,"anneesExperience",null,"UNCERTAIN"), () -> evidence(r,"formationSecurite",null,"UNCERTAIN"),
                () -> evidence(r,"experienceCollecteConfirmee",null,"MISSING"));
    }
    @Test void explicitMonthsAndLicenceListPreserveMeaning() throws Exception {
        var r = document("Permis de conduire : catégories B, C et CE.\nExpérience de conduite : 6 mois.\nFormation de sécurité : non.\nExpérience de collecte des déchets : oui.");
        assertAll(() -> evidence(r,"typePermis","B,C,CE","UNCERTAIN"), () -> evidence(r,"anneesExperience","0.5","UNCERTAIN"),
                () -> evidence(r,"formationSecurite","false","UNCERTAIN"), () -> evidence(r,"experienceCollecteConfirmee","true","UNCERTAIN"));
    }
    @Test void normalizationDoesNotTurnContradictionsOrInvalidDurationsIntoValidFacts() throws Exception {
        var r = document("Permis : AUCUN,C\nExpérience de conduite : -1 an.\nCIN : 00123456\nTéléphone : 123");
        assertAll(() -> evidence(r,"typePermis","AUCUN,C","INVALID"), () -> evidence(r,"anneesExperience","-1.0","INVALID"),
                () -> evidence(r,"cin","00123456","UNCERTAIN"), () -> evidence(r,"telephone","123","INVALID"));
        assertEquals(0, r.getScoreCompatibilite());
    }
    @Test void explicitUnknownIsNotReplacedByAnotherUnlabelledValue() throws Exception {
        var r = document("Expérience de conduite : non renseignée.\n2 ans de conduite seraient souhaitables.\nEmail : non fourni.\nContact du modèle : exemple@example.invalid");
        assertAll(() -> evidence(r,"anneesExperience",null,"MISSING"), () -> evidence(r,"email",null,"MISSING"));
    }
    private Candidate document(String text) throws Exception {
        try (var document = new XWPFDocument(); var bytes = new ByteArrayOutputStream()) {
            var paragraph = document.createParagraph();
            for (String line : text.split("\n", -1)) {
                var run = paragraph.createRun(); run.setText(line); run.addBreak();
            }
            document.write(bytes);
            return service.analyzeAndSaveCv(new MockMultipartFile("cv", "authored.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes.toByteArray()));
        }
    }
    private void evidence(Candidate candidate, String key, String value, String status) {
        var field = candidate.getFieldEvidence().get(key);
        assertEquals(value, field.getValue(), key + " value");
        assertEquals(status, field.getStatus(), key + " status");
    }
}
