package com.municipalite.gestiontournees.dashboard.controller;

import com.municipalite.gestiontournees.alerte.repository.AlerteRepository;
import com.municipalite.gestiontournees.camion.model.StatutCamion;
import com.municipalite.gestiontournees.camion.repository.CamionRepository;
import com.municipalite.gestiontournees.dashboard.dto.DashboardStats;
import com.municipalite.gestiontournees.tournee.model.StatutTournee;
import com.municipalite.gestiontournees.tournee.repository.TourneeRepository;
import com.municipalite.gestiontournees.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERVISEUR')")
public class DashboardController {

    private final UserRepository userRepository;
    private final CamionRepository camionRepository;
    private final TourneeRepository tourneeRepository;
    private final AlerteRepository alerteRepository;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStats> getStats() {
        long agentsActifs = userRepository.findAll().stream()
                .filter(u -> u.isActif() && "CHAUFFEUR".equals(u.getRole().name()))
                .count();

        long camionsTotal = camionRepository.findAll().stream()
                .filter(c -> c.isActif())
                .count();
        long camionsEnTournee = camionRepository.findAll().stream()
                .filter(c -> c.isActif() && c.getStatut() == StatutCamion.EN_TOURNEE)
                .count();
        long camionsDisponibles = camionRepository.findAll().stream()
                .filter(c -> c.isActif() && c.getStatut() == StatutCamion.DISPONIBLE)
                .count();
        long camionsEnMaintenance = camionRepository.findAll().stream()
                .filter(c -> c.isActif() && c.getStatut() == StatutCamion.EN_MAINTENANCE)
                .count();

        long tourneesEnCours = tourneeRepository.findAll().stream()
                .filter(t -> t.getStatut() == StatutTournee.EN_COURS)
                .count();

        long alertesCritiques = alerteRepository.findAll().stream()
                .filter(a -> !a.isResolue() && "CRITICAL".equals(a.getNiveau()))
                .count();

        DashboardStats stats = DashboardStats.builder()
                .agentsActifs(agentsActifs)
                .camionsTotal(camionsTotal)
                .camionsEnTournee(camionsEnTournee)
                .camionsDisponibles(camionsDisponibles)
                .camionsEnMaintenance(camionsEnMaintenance)
                .tourneesEnCours(tourneesEnCours)
                .alertesCritiques(alertesCritiques)
                .build();

        return ResponseEntity.ok(stats);
    }
}
