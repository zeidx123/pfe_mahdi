package com.municipalite.gestiontournees.pointcollecte.service;

import com.municipalite.gestiontournees.pointcollecte.dto.PointCollecteRequest;
import com.municipalite.gestiontournees.pointcollecte.dto.PointCollecteResponse;
import com.municipalite.gestiontournees.pointcollecte.model.PointCollecte;
import com.municipalite.gestiontournees.pointcollecte.model.TypeDechet;
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
        validate(request, true);
        if (pointCollecteRepository.findByReference(request.getReference()).isPresent()) {
            throw new IllegalArgumentException("Cette référence existe déjà");
        }

        PointCollecte point = PointCollecte.builder()
                .reference(request.getReference())
                .adresse(request.getAdresse())
                .zone(request.getZone())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .typeDechet(request.getTypeDechet() == null ? TypeDechet.GENERAL : request.getTypeDechet())
                .niveauRemplissage(request.getNiveauRemplissage() != null ? request.getNiveauRemplissage() : 0)
                .actif(request.getActif() == null || request.getActif())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return mapToResponse(pointCollecteRepository.save(point));
    }

    public PointCollecteResponse update(String id, PointCollecteRequest request) {
        validate(request, false);
        PointCollecte point = pointCollecteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Point de collecte introuvable"));

        if (request.getReference() != null && !java.util.Objects.equals(point.getReference(), request.getReference()) &&
                pointCollecteRepository.findByReference(request.getReference()).isPresent()) {
            throw new IllegalArgumentException("Cette référence est déjà utilisée");
        }

        if (request.getReference() != null) point.setReference(request.getReference());
        if (request.getAdresse() != null) point.setAdresse(request.getAdresse());
        if (request.getZone() != null) point.setZone(request.getZone());
        if (request.getLatitude() != null) point.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) point.setLongitude(request.getLongitude());
        if (request.getTypeDechet() != null) point.setTypeDechet(request.getTypeDechet());
        if (request.getNiveauRemplissage() != null) {
            point.setNiveauRemplissage(request.getNiveauRemplissage());
        }
        if (request.getActif() != null) point.setActif(request.getActif());
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

    private void validate(PointCollecteRequest request, boolean creating) {
        if ((creating && (request.getReference() == null || request.getAdresse() == null
                || request.getLatitude() == null || request.getLongitude() == null))
                || (request.getReference() != null && request.getReference().isBlank())
                || (request.getAdresse() != null && request.getAdresse().isBlank())) {
            throw new IllegalArgumentException("Référence, adresse et coordonnées valides obligatoires");
        }
        if ((request.getLatitude() != null && (!Double.isFinite(request.getLatitude())
                || request.getLatitude() < -90 || request.getLatitude() > 90))
                || (request.getLongitude() != null && (!Double.isFinite(request.getLongitude())
                || request.getLongitude() < -180 || request.getLongitude() > 180))) {
            throw new IllegalArgumentException("Coordonnées du point invalides");
        }
        if (request.getNiveauRemplissage() != null
                && (request.getNiveauRemplissage() < 0 || request.getNiveauRemplissage() > 100)) {
            throw new IllegalArgumentException("Le remplissage doit être compris entre 0 et 100");
        }
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
