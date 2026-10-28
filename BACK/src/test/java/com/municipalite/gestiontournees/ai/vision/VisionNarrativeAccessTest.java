package com.municipalite.gestiontournees.ai.vision;

import com.municipalite.gestiontournees.ai.vision.controller.VisionNarrativeController;
import com.municipalite.gestiontournees.ai.vision.model.ImageAnalysis;
import com.municipalite.gestiontournees.ai.vision.narrative.NarrativeService;
import com.municipalite.gestiontournees.ai.vision.service.ImageAnalysisService;
import com.municipalite.gestiontournees.security.*;
import com.municipalite.gestiontournees.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = VisionNarrativeController.class, properties = "app.bootstrap.enabled=false")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class VisionNarrativeAccessTest {
    @Autowired MockMvc mvc;
    @MockBean ImageAnalysisService vision;
    @MockBean NarrativeService narratives;
    @MockBean AccessService access;
    @MockBean UserService users;
    @MockBean JwtTokenProvider tokens;

    @Test void anonymousCannotRequestANarrative() throws Exception {
        mvc.perform(post("/api/ai/vision/analyses/a1/narrative")).andExpect(status().isUnauthorized());
        verifyNoInteractions(vision, narratives);
    }

    @Test @WithMockUser(roles = "CHAUFFEUR")
    void driverCannotRequestANarrative() throws Exception {
        mvc.perform(post("/api/ai/vision/analyses/a1/narrative")).andExpect(status().isForbidden());
        verifyNoInteractions(vision, narratives);
    }

    @Test @WithMockUser(roles = "SUPERVISEUR")
    void supervisorWithTourAccessGetsTheAnalysis() throws Exception {
        when(vision.getAnalysis("a1")).thenReturn(ImageAnalysis.builder().id("a1").tourneeId("tour").build());
        when(narratives.narrate("a1")).thenReturn(ImageAnalysis.builder().id("a1").tourneeId("tour")
                .narrative("Texte").narrativeSource("TEMPLATE").build());
        mvc.perform(post("/api/ai/vision/analyses/a1/narrative")).andExpect(status().isOk())
                .andExpect(jsonPath("$.narrativeSource").value("TEMPLATE"))
                .andExpect(jsonPath("$.narrative").value("Texte"));
        verify(access).requireSupervisor();
        verify(access).requireTourAccess("tour");
    }

    @Test @WithMockUser(roles = "SUPERVISEUR")
    void tourAccessIsCheckedBeforeAnyGeneration() throws Exception {
        when(vision.getAnalysis("a1")).thenReturn(ImageAnalysis.builder().id("a1").tourneeId("tour").build());
        when(access.requireTourAccess("tour")).thenThrow(new AccessDeniedException("Cette tournée ne vous est pas affectée"));
        mvc.perform(post("/api/ai/vision/analyses/a1/narrative")).andExpect(status().isForbidden());
        verifyNoInteractions(narratives);
    }
}
