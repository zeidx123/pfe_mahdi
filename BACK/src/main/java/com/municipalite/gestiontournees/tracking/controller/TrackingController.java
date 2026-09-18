package com.municipalite.gestiontournees.tracking.controller;

import com.municipalite.gestiontournees.camion.repository.CamionRepository;
import com.municipalite.gestiontournees.security.AccessService;
import com.municipalite.gestiontournees.tournee.model.StatutTournee;
import com.municipalite.gestiontournees.tournee.repository.TourneeRepository;
import com.municipalite.gestiontournees.tracking.model.VehiculePosition;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequiredArgsConstructor
public class TrackingController {
    private final SimpMessagingTemplate messagingTemplate;
    private final AccessService access;
    private final TourneeRepository tours;
    private final CamionRepository camions;
    private final Map<String, VehiculePosition> latestPositions = new ConcurrentHashMap<>();

    @PostMapping("/api/tracking/position")
    public void receivePosition(@Valid @RequestBody VehiculePosition position) {
        var tour = access.requireTourAccess(position.getTourneeId());
        if (tour.getStatut() != StatutTournee.EN_COURS) {
            throw new IllegalArgumentException("Le suivi nécessite une tournée en cours");
        }
        if (!Objects.equals(position.getCamionId(), tour.getCamionId())) {
            throw new IllegalArgumentException("Le camion ne correspond pas à la tournée");
        }
        if (!Double.isFinite(position.getLatitude()) || !Double.isFinite(position.getLongitude())) {
            throw new IllegalArgumentException("Coordonnées GPS invalides");
        }
        position.setTourneeReference(tour.getReference());
        position.setCamionMatricule(camions.findById(tour.getCamionId()).map(c -> c.getMatricule()).orElse(null));
        position.setTimestamp(LocalDateTime.now());
        position.setStale(false);
        latestPositions.put(position.getCamionId(), position);
        messagingTemplate.convertAndSend("/topic/positions", position);
    }

    @GetMapping("/api/tracking/positions")
    public List<VehiculePosition> getAllPositions() {
        access.requireSupervisor();
        LocalDateTime freshnessCutoff = LocalDateTime.now().minusSeconds(60);
        return latestPositions.values().stream().map(position -> {
            boolean active = tours.findById(position.getTourneeId())
                    .map(tour -> tour.getStatut() == StatutTournee.EN_COURS).orElse(false);
            position.setStale(!active || position.getTimestamp().isBefore(freshnessCutoff));
            return position;
        }).toList();
    }
}
