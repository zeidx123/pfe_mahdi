package com.municipalite.gestiontournees.camion.service;

import com.municipalite.gestiontournees.camion.dto.CamionRequest;
import com.municipalite.gestiontournees.camion.dto.CamionResponse;
import com.municipalite.gestiontournees.camion.model.Camion;
import com.municipalite.gestiontournees.camion.repository.CamionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CamionService {

    private final CamionRepository camionRepository;

    public List<CamionResponse> getAllCamions() {
        return camionRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public CamionResponse getCamionById(String id) {
        Camion camion = camionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Camion introuvable"));
        return mapToResponse(camion);
    }

    public CamionResponse createCamion(CamionRequest request) {
        if (camionRepository.findByMatricule(request.getMatricule()).isPresent()) {
            throw new RuntimeException("Ce matricule existe déjà");
        }

        Camion camion = Camion.builder()
                .matricule(request.getMatricule())
                .marque(request.getMarque())
                .modele(request.getModele())
                .annee(request.getAnnee())
                .typeCamion(request.getTypeCamion())
                .capacitePoids(request.getCapacitePoids())
                .capaciteReservoir(request.getCapaciteReservoir())
                .kilometrage(request.getKilometrage())
                .dateMiseEnService(request.getDateMiseEnService())
                .statut(request.getStatut())
                .etat(request.getEtat())
                .numeroESP32(request.getNumeroESP32())
                .dateDerniereMaintenance(request.getDateDerniereMaintenance())
                .prochaineMaintenance(request.getProchaineMaintenance())
                .actif(request.isActif())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return mapToResponse(camionRepository.save(camion));
    }

    public CamionResponse updateCamion(String id, CamionRequest request) {
        Camion camion = camionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Camion introuvable"));

        if (!camion.getMatricule().equals(request.getMatricule()) && 
            camionRepository.findByMatricule(request.getMatricule()).isPresent()) {
            throw new RuntimeException("Ce matricule existe déjà pour un autre camion");
        }

        camion.setMatricule(request.getMatricule());
        camion.setMarque(request.getMarque());
        camion.setModele(request.getModele());
        camion.setAnnee(request.getAnnee());
        camion.setTypeCamion(request.getTypeCamion());
        camion.setCapacitePoids(request.getCapacitePoids());
        camion.setCapaciteReservoir(request.getCapaciteReservoir());
        camion.setKilometrage(request.getKilometrage());
        camion.setDateMiseEnService(request.getDateMiseEnService());
        camion.setStatut(request.getStatut());
        camion.setEtat(request.getEtat());
        camion.setNumeroESP32(request.getNumeroESP32());
        camion.setDateDerniereMaintenance(request.getDateDerniereMaintenance());
        camion.setProchaineMaintenance(request.getProchaineMaintenance());
        camion.setActif(request.isActif());
        camion.setUpdatedAt(LocalDateTime.now());

        return mapToResponse(camionRepository.save(camion));
    }

    public void deleteCamion(String id) {
        Camion camion = camionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Camion introuvable"));
        
        camion.setActif(false);
        camion.setUpdatedAt(LocalDateTime.now());
        camionRepository.save(camion);
    }

    private CamionResponse mapToResponse(Camion camion) {
        return CamionResponse.builder()
                .id(camion.getId())
                .matricule(camion.getMatricule())
                .marque(camion.getMarque())
                .modele(camion.getModele())
                .annee(camion.getAnnee())
                .typeCamion(camion.getTypeCamion())
                .capacitePoids(camion.getCapacitePoids())
                .capaciteReservoir(camion.getCapaciteReservoir())
                .kilometrage(camion.getKilometrage())
                .dateMiseEnService(camion.getDateMiseEnService())
                .statut(camion.getStatut())
                .etat(camion.getEtat())
                .numeroESP32(camion.getNumeroESP32())
                .dateDerniereMaintenance(camion.getDateDerniereMaintenance())
                .prochaineMaintenance(camion.getProchaineMaintenance())
                .actif(camion.isActif())
                .createdAt(camion.getCreatedAt())
                .updatedAt(camion.getUpdatedAt())
                .build();
    }
}
