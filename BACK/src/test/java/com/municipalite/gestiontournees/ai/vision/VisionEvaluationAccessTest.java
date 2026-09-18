package com.municipalite.gestiontournees.ai.vision;

import com.municipalite.gestiontournees.ai.vision.controller.VisionController;
import com.municipalite.gestiontournees.ai.vision.service.ImageAnalysisService;
import com.municipalite.gestiontournees.security.*;
import com.municipalite.gestiontournees.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = VisionController.class, properties = "app.bootstrap.enabled=false")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class VisionEvaluationAccessTest {
    @Autowired MockMvc mvc;
    @MockBean ImageAnalysisService vision;
    @MockBean AccessService access;
    @MockBean UserService users;
    @MockBean JwtTokenProvider tokens;

    @Test void anonymousCannotReadEvaluationConfiguration() throws Exception {
        mvc.perform(get("/api/ai/vision/evaluation-config")).andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser(roles = "CHAUFFEUR")
    void driverCannotRunExperiments() throws Exception {
        mvc.perform(multipart("/api/ai/vision/evaluate")
                .file(new MockMultipartFile("afterPhoto", new byte[]{1}))
                .param("expectedModel", "test-model").param("expectedPromptVersion", "point-cleanliness-v1"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(vision);
    }

    @Test @WithMockUser(roles = "SUPERVISEUR")
    void supervisorCanReadOnlyNonSecretConfiguration() throws Exception {
        when(vision.evaluationConfiguration()).thenReturn(
                new ImageAnalysisService.EvaluationConfiguration("GEMINI", "test-model", "point-cleanliness-v1"));
        mvc.perform(get("/api/ai/vision/evaluation-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.apiKey").doesNotExist());
        verify(access).requireSupervisor();
    }
}
