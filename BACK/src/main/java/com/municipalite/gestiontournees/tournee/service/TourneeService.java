package com.municipalite.gestiontournees.tournee.service;

import com.municipalite.gestiontournees.camion.model.Camion;
import com.municipalite.gestiontournees.camion.repository.CamionRepository;
import com.municipalite.gestiontournees.pointcollecte.dto.PointCollecteResponse;
import com.municipalite.gestiontournees.pointcollecte.model.PointCollecte;
import com.municipalite.gestiontournees.pointcollecte.repository.PointCollecteRepository;
import com.municipalite.gestiontournees.tournee.dto.TourneeRequest;
import com.municipalite.gestiontournees.tournee.dto.TourneeResponse;
import com.municipalite.gestiontournees.tournee.model.Tournee;
import com.municipalite.gestiontournees.tournee.repository.TourneeRepository;
import com.municipalite.gestiontournees.user.model.User;
import com.municipalite.gestiontournees.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TourneeService {

    private final TourneeRepository tourneeRepository;
    private final CamionRepository camionRepository;
    private final UserRepository userRepository;
    private final PointCollecteRepository pointCollecteRepository;
    private final TourneeRouteOptimizationService routeOptimizationService;

    public List<TourneeResponse> getAll() {
        return tourneeRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<TourneeResponse> getTourneesByAgent(String identifier) {
        User agent = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByMatricule(identifier))
                .or(() -> userRepository.findById(identifier))
                .orElseThrow(() -> new RuntimeException("Agent introuvable : " + identifier));
        return tourneeRepository.findAll().stream()
                .filter(t -> agent.getId().equals(t.getChauffeurId()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TourneeResponse getById(String id) {
        Tournee tournee = tourneeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tournée introuvable"));
        return mapToResponse(tournee);
    }

    public TourneeResponse create(TourneeRequest request) {
        if (tourneeRepository.findByReference(request.getReference()).isPresent()) {
            throw new RuntimeException("Cette référence de tournée existe déjà");
        }

        Tournee tournee = Tournee.builder()
                .reference(request.getReference())
                .nom(request.getNom())
                .description(request.getDescription())
                .datePrevue(request.getDatePrevue())
                .heureDepartPrevue(request.getHeureDepartPrevue())
                .camionId(request.getCamionId())
                .chauffeurId(request.getChauffeurId())
                .pointsCollecteIds(request.getPointsCollecteIds())
                .statut(request.getStatut() != null ? request.getStatut() : com.municipalite.gestiontournees.tournee.model.StatutTournee.PLANIFIEE)
                .notes(request.getNotes())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // === Optimisation IA : calcul du chemin le plus court ===
        applyRouteOptimization(tournee);

        return mapToResponse(tourneeRepository.save(tournee));
    }

    public TourneeResponse update(String id, TourneeRequest request) {
        Tournee tournee = tourneeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tournée introuvable"));

        if (!tournee.getReference().equals(request.getReference()) &&
                tourneeRepository.findByReference(request.getReference()).isPresent()) {
            throw new RuntimeException("Cette référence est déjà utilisée");
        }

        tournee.setReference(request.getReference());
        tournee.setNom(request.getNom());
        tournee.setDescription(request.getDescription());
        tournee.setDatePrevue(request.getDatePrevue());
        tournee.setHeureDepartPrevue(request.getHeureDepartPrevue());
        tournee.setCamionId(request.getCamionId());
        tournee.setChauffeurId(request.getChauffeurId());
        tournee.setPointsCollecteIds(request.getPointsCollecteIds());
        if (request.getStatut() != null) tournee.setStatut(request.getStatut());
        tournee.setNotes(request.getNotes());
        tournee.setUpdatedAt(LocalDateTime.now());

        // === Optimisation IA : recalculer après chaque modification ===
        applyRouteOptimization(tournee);

        return mapToResponse(tourneeRepository.save(tournee));
    }

    /**
     * Recalcule manuellement l'optimisation IA pour une tournée existante.
     */
    public TourneeResponse reoptimiser(String id) {
        Tournee tournee = tourneeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tournée introuvable"));
        applyRouteOptimization(tournee);
        tournee.setUpdatedAt(LocalDateTime.now());
        return mapToResponse(tourneeRepository.save(tournee));
    }

    /**
     * Applique l'algorithme d'optimisation IA et met à jour les champs de la tournée.
     */
    private void applyRouteOptimization(Tournee tournee) {
        if (tournee.getPointsCollecteIds() == null || tournee.getPointsCollecteIds().isEmpty()) {
            log.info("Tournée {} : aucun point de collecte à optimiser.", tournee.getReference());
            return;
        }

        List<PointCollecte> points = pointCollecteRepository.findAllById(tournee.getPointsCollecteIds());
        if (points.isEmpty()) return;

        try {
            RouteOptimizationResult result = routeOptimizationService.optimize(points);

            // Réordonner les IDs selon l'ordre optimal
            tournee.setPointsCollecteIds(result.getPointsIdsOrdonnes());
            tournee.setPointsCollecteIdsOrdonnes(result.getPointsIdsOrdonnes());
            tournee.setPointsAdressesOrdonnes(result.getPointsAdresses());
            tournee.setDistanceOptimaleKm(result.getDistanceTotaleKm());
            tournee.setDureeEstimeeMinutes(result.getDureeEstimeeMinutes());
            tournee.setDureeFormatee(result.getDureeFormatee());
            tournee.setExplicationOrdre(result.getExplicationOrdre());
            tournee.setOptimisationEffectuee(result.isOptimise());

            log.info("✅ Tournée {} optimisée : {} km / {}", 
                    tournee.getReference(), result.getDistanceTotaleKm(), result.getDureeFormatee());
        } catch (Exception e) {
            log.error("Erreur lors de l'optimisation de la tournée {} : {}", tournee.getReference(), e.getMessage());
        }
    }

    public TourneeResponse updateStatut(String id, com.municipalite.gestiontournees.tournee.model.StatutTournee statut) {
        Tournee tournee = tourneeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tournée introuvable"));
        tournee.setStatut(statut);
        if (statut == com.municipalite.gestiontournees.tournee.model.StatutTournee.EN_COURS) {
            tournee.setHeureDebutReelle(LocalDateTime.now());
        } else if (statut == com.municipalite.gestiontournees.tournee.model.StatutTournee.TERMINEE) {
            tournee.setHeureFinReelle(LocalDateTime.now());
        }
        tournee.setUpdatedAt(LocalDateTime.now());
        return mapToResponse(tourneeRepository.save(tournee));
    }

    public void delete(String id) {
        Tournee tournee = tourneeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tournée introuvable"));
        tournee.setStatut(com.municipalite.gestiontournees.tournee.model.StatutTournee.ANNULEE);
        tournee.setUpdatedAt(LocalDateTime.now());
        tourneeRepository.save(tournee);
    }

    private TourneeResponse mapToResponse(Tournee tournee) {
        String camionMatricule = null;
        if (tournee.getCamionId() != null) {
            camionMatricule = camionRepository.findById(tournee.getCamionId())
                    .map(Camion::getMatricule).orElse("Inconnu");
        }

        String chauffeurNomComplet = null;
        if (tournee.getChauffeurId() != null) {
            chauffeurNomComplet = userRepository.findById(tournee.getChauffeurId())
                    .map(u -> u.getPrenom() + " " + u.getNom()).orElse("Inconnu");
        }

        // Récupérer les points dans l'ordre optimisé (si disponible)
        List<String> orderedIds = tournee.getPointsCollecteIdsOrdonnes() != null && !tournee.getPointsCollecteIdsOrdonnes().isEmpty()
                ? tournee.getPointsCollecteIdsOrdonnes()
                : tournee.getPointsCollecteIds();

        List<PointCollecteResponse> pointsCollecte = new ArrayList<>();
        if (orderedIds != null && !orderedIds.isEmpty()) {
            // Charger les points et les retourner dans l'ordre optimal
            List<PointCollecte> allPoints = pointCollecteRepository.findAllById(orderedIds);
            // Trier selon l'ordre optimal
            pointsCollecte = orderedIds.stream()
                    .map(pId -> allPoints.stream().filter(p -> p.getId().equals(pId)).findFirst().orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .map(p -> PointCollecteResponse.builder()
                            .id(p.getId())
                            .reference(p.getReference())
                            .adresse(p.getAdresse())
                            .zone(p.getZone())
                            .latitude(p.getLatitude())
                            .longitude(p.getLongitude())
                            .typeDechet(p.getTypeDechet())
                            .niveauRemplissage(p.getNiveauRemplissage())
                            .actif(p.isActif())
                            .createdAt(p.getCreatedAt())
                            .updatedAt(p.getUpdatedAt())
                            .build())
                    .collect(Collectors.toList());
        }

        return TourneeResponse.builder()
                .id(tournee.getId())
                .reference(tournee.getReference())
                .nom(tournee.getNom())
                .description(tournee.getDescription())
                .datePrevue(tournee.getDatePrevue())
                .heureDepartPrevue(tournee.getHeureDepartPrevue())
                .camionId(tournee.getCamionId())
                .camionMatricule(camionMatricule)
                .chauffeurId(tournee.getChauffeurId())
                .chauffeurNomComplet(chauffeurNomComplet)
                .pointsCollecteIds(orderedIds)
                .pointsCollecte(pointsCollecte)
                .nombrePoints(orderedIds != null ? orderedIds.size() : 0)
                .statut(tournee.getStatut())
                .heureDebutReelle(tournee.getHeureDebutReelle())
                .heureFinReelle(tournee.getHeureFinReelle())
                .distanceParcourue(tournee.getDistanceParcourue())
                .notes(tournee.getNotes())
                .createdAt(tournee.getCreatedAt())
                .updatedAt(tournee.getUpdatedAt())
                // Données d'optimisation IA
                .pointsCollecteIdsOrdonnes(tournee.getPointsCollecteIdsOrdonnes())
                .pointsAdressesOrdonnes(tournee.getPointsAdressesOrdonnes())
                .distanceOptimaleKm(tournee.getDistanceOptimaleKm())
                .dureeEstimeeMinutes(tournee.getDureeEstimeeMinutes())
                .dureeFormatee(tournee.getDureeFormatee())
                .explicationOrdre(tournee.getExplicationOrdre())
                .optimisationEffectuee(tournee.getOptimisationEffectuee())
                .photosPoints(tournee.getPhotosPoints())
                .photoFin(tournee.getPhotoFin())
                .build();
    }

    public TourneeResponse savePointPhoto(String tourneeId, String pointId, org.springframework.web.multipart.MultipartFile file) {
        Tournee tournee = tourneeRepository.findById(tourneeId)
                .orElseThrow(() -> new RuntimeException("Tournée introuvable"));
        try {
            java.nio.file.Path uploadDir = java.nio.file.Paths.get("uploads/photos");
            java.nio.file.Files.createDirectories(uploadDir);
            String filename = "point_" + tourneeId + "_" + pointId + "_" + System.currentTimeMillis() + getExtension(file.getOriginalFilename());
            java.nio.file.Path filePath = uploadDir.resolve(filename);
            file.transferTo(filePath.toFile());
            if (tournee.getPhotosPoints() == null) tournee.setPhotosPoints(new java.util.HashMap<>());
            tournee.getPhotosPoints().put(pointId, filename);
            tournee.setUpdatedAt(LocalDateTime.now());
            return mapToResponse(tourneeRepository.save(tournee));
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'enregistrement de la photo : " + e.getMessage());
        }
    }

    public TourneeResponse saveFinPhoto(String tourneeId, org.springframework.web.multipart.MultipartFile file) {
        Tournee tournee = tourneeRepository.findById(tourneeId)
                .orElseThrow(() -> new RuntimeException("Tournée introuvable"));
        try {
            java.nio.file.Path uploadDir = java.nio.file.Paths.get("uploads/photos");
            java.nio.file.Files.createDirectories(uploadDir);
            String filename = "fin_" + tourneeId + "_" + System.currentTimeMillis() + getExtension(file.getOriginalFilename());
            java.nio.file.Path filePath = uploadDir.resolve(filename);
            file.transferTo(filePath.toFile());
            tournee.setPhotoFin(filename);
            tournee.setUpdatedAt(LocalDateTime.now());
            return mapToResponse(tourneeRepository.save(tournee));
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'enregistrement de la photo de fin : " + e.getMessage());
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf("."));
    }
}
