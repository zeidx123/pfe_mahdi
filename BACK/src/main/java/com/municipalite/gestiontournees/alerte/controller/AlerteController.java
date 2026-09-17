package com.municipalite.gestiontournees.alerte.controller;

import com.municipalite.gestiontournees.alerte.model.Alerte;
import com.municipalite.gestiontournees.alerte.service.AlerteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alertes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERVISEUR')")
public class AlerteController {

    private final AlerteService alerteService;

    @GetMapping
    public ResponseEntity<List<Alerte>> getAll() {
        return ResponseEntity.ok(alerteService.getAll());
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount() {
        return ResponseEntity.ok(alerteService.getUnreadCount());
    }

    @PatchMapping("/{id}/resolve")
    public ResponseEntity<Alerte> resolve(@PathVariable String id) {
        return ResponseEntity.ok(alerteService.markAsResolved(id));
    }
}
