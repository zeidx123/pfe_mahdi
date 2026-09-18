package com.municipalite.gestiontournees.reclamation.controller;

import com.municipalite.gestiontournees.reclamation.model.Reclamation;
import com.municipalite.gestiontournees.reclamation.service.ReclamationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reclamations")
@RequiredArgsConstructor
public class ReclamationController {

    private final ReclamationService reclamationService;

    @PostMapping
    public ResponseEntity<Reclamation> create(
            @Valid @RequestBody Reclamation reclamation,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(reclamationService.createReclamation(reclamation, userDetails.getUsername()));
    }

    @GetMapping("/mes-reclamations")
    public ResponseEntity<List<Reclamation>> getMesReclamations(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(reclamationService.getReclamationsByChauffeur(userDetails.getUsername()));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public ResponseEntity<List<Reclamation>> getAll() {
        return ResponseEntity.ok(reclamationService.getAllReclamations());
    }

    @PostMapping("/{id}/repondre")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public ResponseEntity<Reclamation> repondre(
            @PathVariable String id,
            @RequestBody java.util.Map<String, String> payload) {
        String reponse = payload.get("reponse");
        return ResponseEntity.ok(reclamationService.repondreReclamation(id, reponse));
    }
}
