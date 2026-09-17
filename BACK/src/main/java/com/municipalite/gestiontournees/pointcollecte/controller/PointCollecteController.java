package com.municipalite.gestiontournees.pointcollecte.controller;

import com.municipalite.gestiontournees.pointcollecte.dto.PointCollecteRequest;
import com.municipalite.gestiontournees.pointcollecte.dto.PointCollecteResponse;
import com.municipalite.gestiontournees.pointcollecte.service.PointCollecteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERVISEUR')")
public class PointCollecteController {

    private final PointCollecteService pointCollecteService;

    @GetMapping
    public ResponseEntity<List<PointCollecteResponse>> getAll() {
        return ResponseEntity.ok(pointCollecteService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PointCollecteResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(pointCollecteService.getById(id));
    }

    @PostMapping
    public ResponseEntity<PointCollecteResponse> create(@Valid @RequestBody PointCollecteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pointCollecteService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PointCollecteResponse> update(
            @PathVariable String id,
            @Valid @RequestBody PointCollecteRequest request) {
        return ResponseEntity.ok(pointCollecteService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        pointCollecteService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
