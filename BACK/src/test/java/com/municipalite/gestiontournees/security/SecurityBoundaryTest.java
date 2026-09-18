package com.municipalite.gestiontournees.security;

import com.municipalite.gestiontournees.agent.controller.AgentController;
import com.municipalite.gestiontournees.agent.service.AgentService;
import com.municipalite.gestiontournees.auth.controller.AuthController;
import com.municipalite.gestiontournees.auth.service.AuthService;
import com.municipalite.gestiontournees.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, AgentController.class}, properties = "app.bootstrap.enabled=false")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SecurityBoundaryTest {
    @Autowired MockMvc mvc;
    @MockBean AuthService auth;
    @MockBean AgentService agents;
    @MockBean UserService users;
    @MockBean JwtTokenProvider tokens;

    @Test void anonymousBulkResetIsNotAllowed() throws Exception {
        mvc.perform(post("/api/agents/reset-all-passwords")).andExpect(status().isUnauthorized());
    }

    @Test void anonymousUploadsAndMeAreNotAllowed() throws Exception {
        mvc.perform(get("/uploads/photos/example.jpg")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void disabledLoginProduces401() throws Exception {
        when(auth.login(any())).thenThrow(new DisabledException("inactive"));
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"driver@test.invalid\",\"password\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser(roles = "CHAUFFEUR")
    void driverCannotManageOtherDrivers() throws Exception {
        mvc.perform(get("/api/agents")).andExpect(status().isForbidden());
    }
}
