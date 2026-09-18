package com.municipalite.gestiontournees.agent.controller;

import com.municipalite.gestiontournees.agent.dto.AgentRequest;
import com.municipalite.gestiontournees.agent.dto.AgentResponse;
import com.municipalite.gestiontournees.agent.service.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERVISEUR')")
public class AgentController {

    private final AgentService agentService;

    @GetMapping
    public ResponseEntity<List<AgentResponse>> getAllAgents() {
        return ResponseEntity.ok(agentService.getAllAgents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgentResponse> getAgentById(@PathVariable String id) {
        return ResponseEntity.ok(agentService.getAgentById(id));
    }

    @PostMapping
    public ResponseEntity<AgentResponse> createAgent(@Valid @RequestBody AgentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agentService.createAgent(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AgentResponse> updateAgent(
            @PathVariable String id,
            @Valid @RequestBody AgentRequest request) {
        return ResponseEntity.ok(agentService.updateAgent(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAgent(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean permanent) {
        if (permanent) {
            agentService.deleteAgentPermanently(id);
        } else {
            agentService.deleteAgent(id);
        }
        return ResponseEntity.noContent().build();
    }

    // Endpoint pour réinitialiser le mot de passe d'un agent à son CIN
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPasswordToCin(@PathVariable String id) {
        agentService.resetPasswordToCin(id);
        return ResponseEntity.ok().build();
    }

}
