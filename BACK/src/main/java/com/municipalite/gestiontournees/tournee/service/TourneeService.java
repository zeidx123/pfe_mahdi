package com.municipalite.gestiontournees.tournee.service;

import com.municipalite.gestiontournees.camion.model.*;
import com.municipalite.gestiontournees.camion.repository.CamionRepository;
import com.municipalite.gestiontournees.pointcollecte.dto.PointCollecteResponse;
import com.municipalite.gestiontournees.pointcollecte.model.PointCollecte;
import com.municipalite.gestiontournees.pointcollecte.repository.PointCollecteRepository;
import com.municipalite.gestiontournees.tournee.dto.*;
import com.municipalite.gestiontournees.tournee.model.*;
import com.municipalite.gestiontournees.tournee.repository.TourneeRepository;
import com.municipalite.gestiontournees.user.model.*;
import com.municipalite.gestiontournees.user.repository.UserRepository;
import com.municipalite.gestiontournees.security.AccessService;
import com.municipalite.gestiontournees.ai.vision.service.ImageAnalysisService;
import com.municipalite.gestiontournees.ai.vision.model.ImageAnalysis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class TourneeService {
    private final TourneeRepository tourneeRepository;
    private final CamionRepository camionRepository;
    private final UserRepository userRepository;
    private final PointCollecteRepository pointCollecteRepository;
    private final TourneeRouteOptimizationService routeOptimizationService;
    private final RoutingProperties routingProperties;
    private final AccessService access;
    private final ImageAnalysisService imageAnalysisService;
    @org.springframework.beans.factory.annotation.Value("${ai.storage.photos-dir:uploads/photos}")
    private String photoDirectory = "uploads/photos";
    private static final Set<String> HUMAN_LABELS = Set.of("CLEANED", "NOT_CLEANED", "OBSTRUCTED", "CANNOT_DETERMINE");
    // One backend process serves the PFE: prevent duplicate provider calls for the same visit.
    private final Set<VisitAnalysisKey> analysesInProgress = ConcurrentHashMap.newKeySet();
    private record VisitAnalysisKey(String tourId, String pointId) {}

    public List<TourneeResponse> getAll() {
        User user = access.currentUser();
        return tourneeRepository.findAll().stream().filter(t -> access.isSupervisor() || user.getId().equals(t.getChauffeurId()))
                .map(this::mapToResponse).toList();
    }
    public List<TourneeResponse> getTourneesByAgent(String identifier) {
        User user = access.currentUser();
        return tourneeRepository.findAll().stream().filter(t -> user.getId().equals(t.getChauffeurId()))
                .map(this::mapToResponse).toList();
    }
    public TourneeResponse getById(String id) { return mapToResponse(load(id)); }

    public TourneeResponse create(TourneeRequest request) {
        access.requireSupervisor(); validateRequest(request);
        if (request.getStatut() != null && request.getStatut() != StatutTournee.PLANIFIEE)
            throw new IllegalArgumentException("Une nouvelle tournée doit être planifiée");
        if (tourneeRepository.findByReference(request.getReference()).isPresent()) throw conflict("Cette référence existe déjà");
        Tournee t = Tournee.builder().reference(request.getReference()).nom(request.getNom()).description(request.getDescription())
                .datePrevue(request.getDatePrevue()).heureDepartPrevue(request.getHeureDepartPrevue())
                .camionId(request.getCamionId()).chauffeurId(request.getChauffeurId())
                .pointsCollecteIds(new ArrayList<>(request.getPointsCollecteIds())).statut(StatutTournee.PLANIFIEE)
                .notes(request.getNotes()).createdAt(now()).build();
        clearRoute(t); ensureVisits(t); return save(t);
    }
    public TourneeResponse update(String id, TourneeRequest request) {
        access.requireSupervisor(); Tournee t = load(id); requirePlanned(t); validateRequest(request);
        if (request.getStatut() != null && request.getStatut() != StatutTournee.PLANIFIEE)
            throw new IllegalArgumentException("Utilisez les actions de démarrage ou d'annulation");
        tourneeRepository.findByReference(request.getReference()).filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw conflict("Cette référence existe déjà"); });
        boolean routeChanged = !Objects.equals(t.getPointsCollecteIds(), request.getPointsCollecteIds());
        t.setReference(request.getReference()); t.setNom(request.getNom()); t.setDescription(request.getDescription());
        t.setDatePrevue(request.getDatePrevue()); t.setHeureDepartPrevue(request.getHeureDepartPrevue());
        t.setCamionId(request.getCamionId()); t.setChauffeurId(request.getChauffeurId()); t.setNotes(request.getNotes());
        t.setPointsCollecteIds(new ArrayList<>(request.getPointsCollecteIds()));
        if (routeChanged) { clearRoute(t); t.setVisits(new ArrayList<>()); ensureVisits(t); }
        return save(t);
    }
    public TourneeResponse reoptimiser(String id) {
        access.requireSupervisor(); Tournee t = load(id); requirePlanned(t);
        try {
            List<PointCollecte> points = orderedPoints(t.getPointsCollecteIds());
            RoadRoute route = routeOptimizationService.optimize(points);
            t.setRoute(route); t.setRouteError(null); t.setOptimisationEffectuee(true);
            t.setPointsCollecteIdsOrdonnes(route.getOrderedPointIds());
            t.setPointsAdressesOrdonnes(route.getOrderedPointIds().stream().map(p -> points.stream().filter(x -> p.equals(x.getId()))
                    .findFirst().map(x -> x.getReference() + " – " + x.getAdresse()).orElse(p)).toList());
            t.setDistanceOptimaleKm(route.getDistanceMeters() / 1000.0);
            t.setDureeEstimeeMinutes((int) Math.ceil(route.getTotalSeconds() / 60.0));
            t.setDureeFormatee(t.getDureeEstimeeMinutes() + " min");
            t.setExplicationOrdre("Itinéraire routier " + route.getAlgorithm() + " avec départ et retour au dépôt. Durée estimée incluant le service. Optimalité non garantie.");
            t.setVisits(new ArrayList<>()); ensureVisits(t); return save(t);
        } catch (RuntimeException e) {
            clearRoute(t); t.setVisits(new ArrayList<>()); ensureVisits(t);
            t.setRouteError(e instanceof ResponseStatusException r ? r.getReason() : e.getMessage());
            save(t); throw e;
        }
    }
    public TourneeResponse start(String id) {
        Tournee t = load(id);
        if (t.getStatut() == StatutTournee.EN_COURS) return mapToResponse(t);
        requirePlanned(t);
        if (t.getRoute() == null || !Boolean.TRUE.equals(t.getOptimisationEffectuee())) throw conflict("Calculez d'abord l'itinéraire routier");
        validateResources(t.getCamionId(), t.getChauffeurId(), t.getPointsCollecteIds());
        validateRouteSnapshot(t);
        if (tourneeRepository.findAll().stream().anyMatch(other -> !id.equals(other.getId()) && other.getStatut() == StatutTournee.EN_COURS
                && (Objects.equals(t.getCamionId(), other.getCamionId()) || Objects.equals(t.getChauffeurId(), other.getChauffeurId()))))
            throw conflict("Le chauffeur ou le camion réalise déjà une tournée");
        Camion truck = camionRepository.findById(t.getCamionId()).orElseThrow();
        if (truck.getStatut() != StatutCamion.DISPONIBLE) throw conflict("Le camion n'est pas disponible");
        t.setStatut(StatutTournee.EN_COURS); t.setHeureDebutReelle(now());
        t.setEstimatedAtStartSeconds(t.getRoute().getTotalSeconds()); t.setEstimatedServiceAtStartSeconds(t.getRoute().getServiceSeconds());
        TourneeResponse response = save(t); truck.setStatut(StatutCamion.EN_TOURNEE); camionRepository.save(truck); return response;
    }
    public TourneeResponse updateStatut(String id, StatutTournee statut) {
        if (statut == StatutTournee.EN_COURS) return start(id);
        if (statut == StatutTournee.ANNULEE) { delete(id); return getById(id); }
        if (statut == StatutTournee.TERMINEE) throw conflict("Confirmez le retour au dépôt avec l'action Terminer");
        Tournee t = load(id);
        if (t.getStatut() != statut) throw conflict("Transition de statut interdite");
        return mapToResponse(t);
    }
    public TourneeResponse finish(String id, boolean returned) {
        Tournee t = load(id);
        if (!returned) throw new IllegalArgumentException("Confirmez le retour au dépôt");
        if (t.getStatut() == StatutTournee.TERMINEE) return mapToResponse(t);
        requireActive(t); ensureVisits(t);
        if (t.getVisits().isEmpty() || t.getVisits().stream().anyMatch(v -> !v.handled())) throw conflict("Tous les points doivent être traités ou signalés");
        if (t.getVisits().stream().anyMatch(v -> v.getStatus() == TourneeVisit.Status.COMPLETED_REPORTED && v.getAfterPhoto() == null))
            throw conflict("Une photo APRÈS est requise pour chaque collecte déclarée terminée");
        t.setReturnedToDepot(true); t.setHeureFinReelle(now()); t.setStatut(StatutTournee.TERMINEE);
        TourneeResponse response = save(t); releaseTruck(t); return response;
    }
    public void delete(String id) {
        access.requireSupervisor(); Tournee t = load(id);
        if (t.getStatut() == StatutTournee.TERMINEE) throw conflict("Une tournée terminée ne peut pas être annulée");
        boolean active = t.getStatut() == StatutTournee.EN_COURS;
        t.setStatut(StatutTournee.ANNULEE); save(t); if (active) releaseTruck(t);
    }
    public TourneeResponse arrive(String id, String pointId) {
        Tournee t = load(id); requireActive(t); TourneeVisit v = currentVisit(t, pointId);
        if (v.getArrivedAt() == null) { v.setArrivedAt(now()); v.setStatus(TourneeVisit.Status.ARRIVED); v.setLegacyProgressMissing(false); }
        return save(t);
    }
    public TourneeResponse completeVisit(String id, String pointId, VisitCompletionRequest request) {
        Tournee t = load(id); requireActive(t); TourneeVisit v = visit(t, pointId);
        if (v.handled()) return mapToResponse(t);
        currentVisit(t, pointId);
        if (request.getOutcome() == null) throw new IllegalArgumentException("Résultat de visite obligatoire");
        if (request.getOutcome() == VisitCompletionRequest.Outcome.COMPLETED) {
            if (v.getArrivedAt() == null || v.getAfterPhoto() == null) throw conflict("Confirmez l'arrivée et envoyez une photo APRÈS");
            v.setStatus(TourneeVisit.Status.COMPLETED_REPORTED);
        } else {
            if (request.getNotes() == null || request.getNotes().isBlank()) throw new IllegalArgumentException("Expliquez le problème rencontré");
            v.setStatus(TourneeVisit.Status.ISSUE_REPORTED);
        }
        if (request.getDriverAssessment() != null && !HUMAN_LABELS.contains(request.getDriverAssessment()))
            throw new IllegalArgumentException("Évaluation du chauffeur invalide");
        v.setDriverOutcome(request.getOutcome().name()); v.setDriverAssessment(request.getDriverAssessment());
        v.setNotes(request.getNotes()); v.setCompletedAt(now()); v.setLegacyProgressMissing(false); return save(t);
    }
    public TourneeResponse savePointPhoto(String id, String pointId, MultipartFile file) { return savePointPhoto(id, pointId, file, "AFTER"); }
    public TourneeResponse savePointPhoto(String id, String pointId, MultipartFile file, String stage) {
        Tournee t = load(id); requireActive(t); TourneeVisit v = currentVisit(t, pointId);
        if (v.getArrivedAt() == null) throw conflict("Confirmez l'arrivée avant la capture");
        if (!Set.of("BEFORE", "AFTER").contains(stage)) throw new IllegalArgumentException("Étape photo invalide");
        String filename = storePhoto(file);
        if (stage.equals("BEFORE")) v.setBeforePhoto(filename);
        else { v.setAfterPhoto(filename); if (t.getPhotosPoints() == null) t.setPhotosPoints(new HashMap<>()); t.getPhotosPoints().put(pointId, filename); }
        v.setAnalysisId(null); v.setAnalysisStatus(null);
        v.setReviewLabel(null); v.setReviewNotes(null); v.setReviewedBy(null); v.setReviewedAt(null);
        return save(t);
    }
    public TourneeResponse saveFinPhoto(String id, MultipartFile file) {
        Tournee t = load(id); requireActive(t); t.setPhotoFin(storePhoto(file)); return save(t);
    }
    public TourneeResponse analyzeVisit(String id, String pointId) {
        Tournee t = load(id); TourneeVisit v = visit(t, pointId);
        if (v.getAfterPhoto() == null) throw conflict("Une photo APRÈS est nécessaire à l'analyse");
        VisitAnalysisKey key = new VisitAnalysisKey(id, pointId);
        if (!analysesInProgress.add(key))
            throw conflict("Une analyse est déjà en cours pour ce point. Actualisez avant de réessayer.");
        try {
            // Refresh after taking the guard: an earlier request may have just saved its result.
            t = load(id); v = visit(t, pointId);
            if (v.getAfterPhoto() == null) throw conflict("Une photo APRÈS est nécessaire à l'analyse");
            String after = v.getAfterPhoto(), before = v.getBeforePhoto();
            String afterUrl = "/uploads/photos/" + after;
            String beforeUrl = before == null ? null : "/uploads/photos/" + before;
            Optional<ImageAnalysis> stored = imageAnalysisService.reusableVisitAnalysis(v.getAnalysisId(),
                    afterUrl, beforeUrl, pointId, id, t.getChauffeurId());
            if (stored.isPresent()) {
                t = load(id); v = visit(t, pointId);
                if (!Objects.equals(after, v.getAfterPhoto()) || !Objects.equals(before, v.getBeforePhoto()))
                    throw conflict("Les photos ont changé pendant l'analyse. Analysez les nouvelles preuves.");
                if (!Objects.equals(v.getAnalysisStatus(), stored.get().getStatus())) {
                    v.setAnalysisStatus(stored.get().getStatus()); return save(t);
                }
                return mapToResponse(t);
            }
            ImageAnalysis result = imageAnalysisService.analyzeStoredEvidence(afterUrl,
                    beforeUrl, pointId, id, t.getChauffeurId());
            // Re-read after the provider call so a concurrent stop completion isn't overwritten.
            t = load(id); v = visit(t, pointId);
            if (!Objects.equals(after, v.getAfterPhoto()) || !Objects.equals(before, v.getBeforePhoto()))
                throw conflict("Les photos ont changé pendant l'analyse. Analysez les nouvelles preuves.");
            v.setAnalysisId(result.getId()); v.setAnalysisStatus(result.getStatus()); return save(t);
        } finally {
            analysesInProgress.remove(key);
        }
    }
    public TourneeResponse reviewVisit(String id, String pointId, VisitReviewRequest request) {
        access.requireSupervisor(); Tournee t = load(id); TourneeVisit v = visit(t, pointId);
        if (!HUMAN_LABELS.contains(request.getLabel())) throw new IllegalArgumentException("Étiquette de revue invalide");
        if (v.getAfterPhoto() == null && v.getBeforePhoto() == null) throw conflict("Aucune preuve visuelle à examiner");
        v.setReviewLabel(request.getLabel()); v.setReviewNotes(request.getNotes()); v.setReviewedBy(access.currentUser().getId()); v.setReviewedAt(now());
        if (v.getAnalysisId() != null) imageAnalysisService.review(v.getAnalysisId(), request.getLabel(), request.getNotes(), access.currentUser().getId());
        return save(t);
    }
    private String storePhoto(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024) throw new IllegalArgumentException("Photo vide ou supérieure à 10 Mo");
        String type = file.getContentType();
        if (!Set.of("image/jpeg", "image/png").contains(type == null ? "" : type)) throw new IllegalArgumentException("Photo JPEG ou PNG requise");
        try {
            try (var stream = file.getInputStream()) { if (ImageIO.read(stream) == null) throw new IllegalArgumentException("Image illisible"); }
            Path directory = Path.of(photoDirectory).toAbsolutePath().normalize(); Files.createDirectories(directory);
            String filename = UUID.randomUUID() + (type.equals("image/png") ? ".png" : ".jpg");
            file.transferTo(directory.resolve(filename)); return filename;
        } catch (java.io.IOException e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Enregistrement impossible. Réessayez."); }
    }
    private Tournee load(String id) { Tournee t = access.requireTourAccess(id); ensureVisits(t); return t; }
    private TourneeResponse save(Tournee t) { t.setUpdatedAt(now()); return mapToResponse(tourneeRepository.save(t)); }
    private void requirePlanned(Tournee t) { if (t.getStatut() != StatutTournee.PLANIFIEE) throw conflict("Seule une tournée planifiée peut être modifiée"); }
    private void requireActive(Tournee t) { if (t.getStatut() != StatutTournee.EN_COURS) throw conflict("La tournée doit être en cours"); }
    private void validateRequest(TourneeRequest r) { validateResources(r.getCamionId(), r.getChauffeurId(), r.getPointsCollecteIds()); }
    private void validateResources(String truckId, String driverId, List<String> points) {
        Camion truck = camionRepository.findById(truckId).orElseThrow(() -> new IllegalArgumentException("Camion introuvable"));
        if (!truck.isActif() || truck.getStatut() == StatutCamion.EN_MAINTENANCE) throw new IllegalArgumentException("Camion inactif ou en maintenance");
        User driver = userRepository.findById(driverId).orElseThrow(() -> new IllegalArgumentException("Chauffeur introuvable"));
        if (!driver.isActif() || driver.getRole() != Role.CHAUFFEUR) throw new IllegalArgumentException("Chauffeur actif requis");
        if (points == null || points.isEmpty() || points.size() > routingProperties.getMaxPoints() || new HashSet<>(points).size() != points.size())
            throw new IllegalArgumentException("Sélectionnez de 1 à " + routingProperties.getMaxPoints() + " points distincts");
        for (PointCollecte p : orderedPoints(points)) {
            if (!p.isActif()) throw new IllegalArgumentException("Point inactif : " + p.getReference());
            TourneeRouteOptimizationService.validateCoordinate(p.getLatitude(), p.getLongitude(), p.getReference());
        }
    }
    private List<PointCollecte> orderedPoints(List<String> ids) {
        if (ids == null) throw new IllegalArgumentException("Points obligatoires");
        List<PointCollecte> points = pointCollecteRepository.findAllById(ids);
        return ids.stream().map(id -> points.stream().filter(p -> Objects.equals(id, p.getId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Point introuvable : " + id))).toList();
    }
    private void ensureVisits(Tournee t) {
        if (t.getVisits() != null && !t.getVisits().isEmpty()) return;
        List<String> ids = t.getRoute() != null ? t.getRoute().getOrderedPointIds() : t.getPointsCollecteIds();
        List<TourneeVisit> visits = new ArrayList<>();
        if (ids != null) for (String id : ids) visits.add(TourneeVisit.builder().pointId(id).order(visits.size())
                .plannedServiceSeconds(routingProperties.getServiceSeconds())
                // Historical generic photos have no recorded BEFORE/AFTER stage or completion event.
                .legacyPhoto(t.getPhotosPoints() == null ? null : t.getPhotosPoints().get(id))
                .legacyProgressMissing(t.getStatut() != StatutTournee.PLANIFIEE
                        || (t.getPhotosPoints() != null && t.getPhotosPoints().containsKey(id))).build());
        t.setVisits(visits);
    }
    private TourneeVisit visit(Tournee t, String pointId) { ensureVisits(t); return t.getVisits().stream().filter(v -> pointId.equals(v.getPointId())).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Ce point n'appartient pas à la tournée")); }
    private TourneeVisit currentVisit(Tournee t, String pointId) {
        TourneeVisit v = visit(t, pointId);
        TourneeVisit current = t.getVisits().stream().filter(x -> !x.handled()).findFirst().orElseThrow(() -> conflict("Tous les points sont traités"));
        if (v != current) throw conflict("Traitez d'abord le point courant"); return v;
    }
    private void clearRoute(Tournee t) {
        t.setRoute(null); t.setRouteError("Itinéraire routier à calculer"); t.setOptimisationEffectuee(false);
        t.setPointsCollecteIdsOrdonnes(null); t.setPointsAdressesOrdonnes(null); t.setDistanceOptimaleKm(null);
        t.setDureeEstimeeMinutes(null); t.setDureeFormatee(null); t.setExplicationOrdre(null);
    }
    private void releaseTruck(Tournee t) {
        boolean used = tourneeRepository.findAll().stream().anyMatch(other -> !Objects.equals(other.getId(), t.getId())
                && other.getStatut() == StatutTournee.EN_COURS && Objects.equals(other.getCamionId(), t.getCamionId()));
        if (!used) camionRepository.findById(t.getCamionId()).ifPresent(truck -> { if (truck.getStatut() == StatutCamion.EN_TOURNEE) {
            truck.setStatut(StatutCamion.DISPONIBLE); camionRepository.save(truck); } });
    }
    private Long observedService(Tournee t) {
        List<TourneeVisit> measured = t.getVisits().stream().filter(v -> v.getArrivedAt() != null && v.getCompletedAt() != null).toList();
        return measured.isEmpty() ? null : measured.stream().mapToLong(v -> Duration.between(v.getArrivedAt(), v.getCompletedAt()).getSeconds()).sum();
    }
    private void validateRouteSnapshot(Tournee t) {
        RoadRoute route = t.getRoute();
        if (route.getMatrixCoordinates() == null || route.getMatrixPointIds() == null
                || route.getMatrixCoordinates().size() != route.getMatrixPointIds().size())
            throw conflict("Recalculez l'itinéraire pour enregistrer ses coordonnées");
        for (PointCollecte point : orderedPoints(t.getPointsCollecteIds())) {
            int index = route.getMatrixPointIds().indexOf(point.getId());
            if (index < 0 || !Objects.equals(route.getMatrixCoordinates().get(index), List.of(point.getLongitude(), point.getLatitude())))
                throw conflict("Les coordonnées des points ont changé. Recalculez l'itinéraire");
        }
        if (routingProperties.getDepotLatitude() != null && routingProperties.getDepotLongitude() != null
                && !Objects.equals(route.getMatrixCoordinates().get(0), List.of(routingProperties.getDepotLongitude(), routingProperties.getDepotLatitude())))
            throw conflict("Le dépôt a changé. Recalculez l'itinéraire");
    }
    private Double routeCoordinate(Tournee t, PointCollecte point, int axis) {
        RoadRoute route = t.getRoute();
        if (route != null && route.getMatrixPointIds() != null && route.getMatrixCoordinates() != null) {
            int index = route.getMatrixPointIds().indexOf(point.getId());
            if (index >= 0 && index < route.getMatrixCoordinates().size()) return route.getMatrixCoordinates().get(index).get(axis);
        }
        return axis == 0 ? point.getLongitude() : point.getLatitude();
    }
    private LocalDateTime now() { return LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS); }
    private ResponseStatusException conflict(String reason) { return new ResponseStatusException(HttpStatus.CONFLICT, reason); }
    private TourneeResponse mapToResponse(Tournee tournee) {
        ensureVisits(tournee);
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
                            .latitude(routeCoordinate(tournee, p, 1))
                            .longitude(routeCoordinate(tournee, p, 0))
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
                .pointsCollecteIds(tournee.getPointsCollecteIds())
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
                .visits(tournee.getVisits()).route(tournee.getRoute()).routeError(tournee.getRouteError())
                .returnedToDepot(tournee.isReturnedToDepot())
                .estimatedAtStartSeconds(tournee.getEstimatedAtStartSeconds())
                .estimatedServiceAtStartSeconds(tournee.getEstimatedServiceAtStartSeconds())
                .actualDurationSeconds(tournee.getHeureDebutReelle() != null && tournee.getHeureFinReelle() != null
                        ? Duration.between(tournee.getHeureDebutReelle(), tournee.getHeureFinReelle()).getSeconds() : null)
                .observedServiceSeconds(observedService(tournee))
                .measuredServiceStops((int) tournee.getVisits().stream().filter(v -> v.getArrivedAt() != null && v.getCompletedAt() != null).count())
                .build();
    }


}
