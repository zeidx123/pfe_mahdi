package com.municipalite.gestiontournees.pointcollecte.service;

import com.municipalite.gestiontournees.pointcollecte.dto.PointCollecteRequest;
import com.municipalite.gestiontournees.pointcollecte.dto.PointCollecteResponse;
import com.municipalite.gestiontournees.pointcollecte.model.PointCollecte;
import com.municipalite.gestiontournees.pointcollecte.repository.PointCollecteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PointCollecteService {

    private final PointCollecteRepository pointCollecteRepository;

    public List<PointCollecteResponse> getAll() {
        return pointCollecteRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public PointCollecteResponse getById(String id) {
        PointCollecte point = pointCollecteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Point de collecte introuvable"));
        return mapToResponse(point);
    }

    public PointCollecteResponse create(PointCollecteRequest request) {
        if (pointCollecteRepository.findByReference(request.getReference()).isPresent()) {
            throw new RuntimeException("Cette référence existe déjà");
        }

        PointCollecte point = PointCollecte.builder()
                .reference(request.getReference())
                .adresse(request.getAdresse())
                .zone(request.getZone())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .typeDechet(request.getTypeDechet())
                .niveauRemplissage(request.getNiveauRemplissage() != null ? request.getNiveauRemplissage() : 0)
                .actif(request.isActif())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return mapToResponse(pointCollecteRepository.save(point));
    }

    public PointCollecteResponse update(String id, PointCollecteRequest request) {
        PointCollecte point = pointCollecteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Point de collecte introuvable"));

        if (!point.getReference().equals(request.getReference()) &&
                pointCollecteRepository.findByReference(request.getReference()).isPresent()) {
            throw new RuntimeException("Cette référence est déjà utilisée");
        }

        point.setReference(request.getReference());
        point.setAdresse(request.getAdresse());
        point.setZone(request.getZone());
        point.setLatitude(request.getLatitude());
        point.setLongitude(request.getLongitude());
        point.setTypeDechet(request.getTypeDechet());
        if (request.getNiveauRemplissage() != null) {
            point.setNiveauRemplissage(request.getNiveauRemplissage());
        }
        point.setActif(request.isActif());
        point.setUpdatedAt(LocalDateTime.now());

        return mapToResponse(pointCollecteRepository.save(point));
    }

    public void delete(String id) {
        PointCollecte point = pointCollecteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Point de collecte introuvable"));
        point.setActif(false);
        point.setUpdatedAt(LocalDateTime.now());
        pointCollecteRepository.save(point);
    }

    public PointCollecteResponse mapToResponse(PointCollecte point) {
        return PointCollecteResponse.builder()
                .id(point.getId())
                .reference(point.getReference())
                .adresse(point.getAdresse())
                .zone(point.getZone())
                .latitude(point.getLatitude())
                .longitude(point.getLongitude())
                .typeDechet(point.getTypeDechet())
                .niveauRemplissage(point.getNiveauRemplissage())
                .actif(point.isActif())
                .createdAt(point.getCreatedAt())
                .updatedAt(point.getUpdatedAt())
                .build();
    }
}
