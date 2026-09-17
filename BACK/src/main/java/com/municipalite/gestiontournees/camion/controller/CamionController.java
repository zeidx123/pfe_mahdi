package com.municipalite.gestiontournees.camion.controller;

import com.municipalite.gestiontournees.camion.dto.CamionRequest;
import com.municipalite.gestiontournees.camion.dto.CamionResponse;
import com.municipalite.gestiontournees.camion.service.CamionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/camions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERVISEUR')")
public class CamionController {

    private final CamionService camionService;

    @GetMapping
    public ResponseEntity<List<CamionResponse>> getAllCamions() {
        return ResponseEntity.ok(camionService.getAllCamions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CamionResponse> getCamionById(@PathVariable String id) {
        return ResponseEntity.ok(camionService.getCamionById(id));
    }

    @PostMapping
    public ResponseEntity<CamionResponse> createCamion(@Valid @RequestBody CamionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(camionService.createCamion(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CamionResponse> updateCamion(
            @PathVariable String id, 
            @Valid @RequestBody CamionRequest request) {
        return ResponseEntity.ok(camionService.updateCamion(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCamion(@PathVariable String id) {
        camionService.deleteCamion(id);
        return ResponseEntity.noContent().build();
    }
}
