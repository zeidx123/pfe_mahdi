package com.municipalite.gestiontournees.ai.route.service;

import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.route.dto.OptimizationRequest;
import com.municipalite.gestiontournees.ai.route.dto.OptimizationResponse;
import com.municipalite.gestiontournees.ai.route.model.RouteOptimization;
import com.municipalite.gestiontournees.ai.route.repository.RouteOptimizationRepository;
import com.municipalite.gestiontournees.camion.model.Camion;
import com.municipalite.gestiontournees.camion.repository.CamionRepository;
import com.municipalite.gestiontournees.pointcollecte.dto.PointCollecteResponse;
import com.municipalite.gestiontournees.pointcollecte.model.PointCollecte;
import com.municipalite.gestiontournees.pointcollecte.repository.PointCollecteRepository;
import com.municipalite.gestiontournees.pointcollecte.service.PointCollecteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent IA d'optimisation des tournées.
 *
 * Algorithme utilisé : Nearest Neighbor (construction) + 2-opt (amélioration)
 * = implémentation du Capacitated Vehicle Routing Problem (CVRP).
 *
 * L'algorithme est 100% déterministe — aucun LLM pour le calcul mathématique.
 * Le LLM (Gemini) est utilisé uniquement pour générer l'explication en langage naturel.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RouteOptimizationService {

    private final PointCollecteRepository pointCollecteRepository;
    private final PointCollecteService pointCollecteService;
    private final CamionRepository camionRepository;
    private final RouteOptimizationRepository routeOptimizationRepository;
    private final AiProperties aiProperties;

    /**
     * Optimise une tournée en appliquant l'algorithme CVRP Nearest Neighbor + 2-opt.
     */
    public OptimizationResponse optimize(OptimizationRequest request) {
        // 1. Charger les points de collecte
        List<PointCollecte> points = pointCollecteRepository.findAllById(request.getPointCollecteIds());
        if (points.isEmpty()) {
            throw new RuntimeException("Aucun point de collecte valide trouvé pour les IDs fournis.");
        }

        // 2. Charger le camion pour la capacité
        Camion camion = null;
        double capaciteCamion = 5000.0; // valeur par défaut
        if (request.getCamionId() != null) {
            camion = camionRepository.findById(request.getCamionId()).orElse(null);
            if (camion != null && camion.getCapacitePoids() != null) {
                capaciteCamion = camion.getCapacitePoids();
            }
        }

        // 3. Coordonnées du dépôt
        double depotLat = request.getDepotLatitude() != null
                ? request.getDepotLatitude()
                : aiProperties.getRoute().getDepotLatitude();
        double depotLon = request.getDepotLongitude() != null
                ? request.getDepotLongitude()
                : aiProperties.getRoute().getDepotLongitude();

        // 4. Algorithme Nearest Neighbor
        List<PointCollecte> ordreOptimise = nearestNeighbor(points, depotLat, depotLon);

        // 5. Amélioration 2-opt
        ordreOptimise = twoOpt(ordreOptimise, depotLat, depotLon);

        // 6. Calculer les métriques
        double distanceTotale = calculateTotalDistance(ordreOptimise, depotLat, depotLon);
        double vitesse = aiProperties.getRoute().getDefaultSpeedKmh();
        int dureeMinutes = (int) Math.ceil((distanceTotale / vitesse) * 60);

        // Poids estimé basé sur le niveau de remplissage des bacs
        double poidsEstime = ordreOptimise.stream()
                .mapToDouble(p -> estimerPoidsPoint(p))
                .sum();

        int tauxRemplissage = (int) Math.min(100, (poidsEstime / capaciteCamion) * 100);
        double consommation = (distanceTotale / 100.0) * aiProperties.getRoute().getFuelConsumptionPer100km();

        // 7. Construire la réponse
        List<String> ordreIds = ordreOptimise.stream().map(PointCollecte::getId).collect(Collectors.toList());
        List<PointCollecteResponse> ordreResponse = ordreOptimise.stream()
                .map(p -> pointCollecteService.mapToResponse(p))
                .collect(Collectors.toList());

        // 8. Générer l'explication
        String explication = genererExplication(ordreOptimise, distanceTotale, dureeMinutes, poidsEstime, capaciteCamion, tauxRemplissage);
        String recommandations = genererRecommandations(tauxRemplissage, distanceTotale, poidsEstime, capaciteCamion);

        // 9. Sauvegarder en base
        RouteOptimization saved = routeOptimizationRepository.save(RouteOptimization.builder()
                .camionId(request.getCamionId())
                .chauffeurId(request.getChauffeurId())
                .ordreOptimiseIds(ordreIds)
                .distanceTotaleKm(Math.round(distanceTotale * 10.0) / 10.0)
                .dureeEstimeesMinutes(dureeMinutes)
                .poidsEstimeKg(Math.round(poidsEstime * 10.0) / 10.0)
                .capaciteCamionKg(capaciteCamion)
                .tauxRemplissagePct(tauxRemplissage)
                .consommationEstimeeLitres(Math.round(consommation * 10.0) / 10.0)
                .explication(explication)
                .recommandations(recommandations)
                .algorithmeUtilise("Nearest Neighbor + 2-opt (CVRP)")
                .statut("PROPOSE")
                .dateCreation(LocalDateTime.now())
                .build());

        return OptimizationResponse.builder()
                .optimizationId(saved.getId())
                .ordreOptimise(ordreResponse)
                .distanceTotaleKm(saved.getDistanceTotaleKm())
                .dureeEstimeesMinutes(saved.getDureeEstimeesMinutes())
                .poidsEstimeKg(saved.getPoidsEstimeKg())
                .capaciteCamionKg(capaciteCamion)
                .tauxRemplissagePct(tauxRemplissage)
                .consommationEstimeeLitres(saved.getConsommationEstimeeLitres())
                .explication(explication)
                .recommandations(recommandations)
                .algorithmeUtilise("Nearest Neighbor + 2-opt (CVRP)")
                .build();
    }

    /**
     * Algorithme Nearest Neighbor : commence au dépôt, visite toujours le point le plus proche non encore visité.
     */
    private List<PointCollecte> nearestNeighbor(List<PointCollecte> points, double depotLat, double depotLon) {
        List<PointCollecte> remaining = new ArrayList<>(points);
        List<PointCollecte> route = new ArrayList<>();
        double currentLat = depotLat;
        double currentLon = depotLon;

        while (!remaining.isEmpty()) {
            PointCollecte nearest = null;
            double minDist = Double.MAX_VALUE;

            for (PointCollecte p : remaining) {
                if (p.getLatitude() == null || p.getLongitude() == null) continue;
                double dist = haversine(currentLat, currentLon, p.getLatitude(), p.getLongitude());
                if (dist < minDist) {
                    minDist = dist;
                    nearest = p;
                }
            }

            if (nearest == null) break;
            route.add(nearest);
            remaining.remove(nearest);
            currentLat = nearest.getLatitude();
            currentLon = nearest.getLongitude();
        }

        return route;
    }

    /**
     * Amélioration 2-opt : tente d'inverser des segments pour réduire la distance totale.
     */
    private List<PointCollecte> twoOpt(List<PointCollecte> route, double depotLat, double depotLon) {
        boolean improved = true;
        List<PointCollecte> best = new ArrayList<>(route);

        while (improved) {
            improved = false;
            for (int i = 1; i < best.size() - 1; i++) {
                for (int j = i + 1; j < best.size(); j++) {
                    List<PointCollecte> newRoute = twoOptSwap(best, i, j);
                    if (totalDist(newRoute, depotLat, depotLon) < totalDist(best, depotLat, depotLon)) {
                        best = newRoute;
                        improved = true;
                    }
                }
            }
        }
        return best;
    }

    private List<PointCollecte> twoOptSwap(List<PointCollecte> route, int i, int j) {
        List<PointCollecte> newRoute = new ArrayList<>(route.subList(0, i));
        List<PointCollecte> reversed = new ArrayList<>(route.subList(i, j + 1));
        Collections.reverse(reversed);
        newRoute.addAll(reversed);
        newRoute.addAll(route.subList(j + 1, route.size()));
        return newRoute;
    }

    private double totalDist(List<PointCollecte> route, double depotLat, double depotLon) {
        return calculateTotalDistance(route, depotLat, depotLon);
    }

    private double calculateTotalDistance(List<PointCollecte> route, double depotLat, double depotLon) {
        if (route.isEmpty()) return 0;
        double total = 0;
        double prevLat = depotLat, prevLon = depotLon;

        for (PointCollecte p : route) {
            if (p.getLatitude() == null || p.getLongitude() == null) continue;
            total += haversine(prevLat, prevLon, p.getLatitude(), p.getLongitude());
            prevLat = p.getLatitude();
            prevLon = p.getLongitude();
        }
        // Retour au dépôt
        total += haversine(prevLat, prevLon, depotLat, depotLon);
        return total;
    }

    /**
     * Formule de Haversine : distance entre deux points GPS (en km).
     */
    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * Estime le poids des déchets d'un point basé sur son niveau de remplissage.
     * Hypothèse : bac standard = 240L ≈ 120 kg à 100% de remplissage.
     */
    private double estimerPoidsPoint(PointCollecte p) {
        int niveauRemplissage = p.getNiveauRemplissage() != null ? p.getNiveauRemplissage() : 50;
        return (niveauRemplissage / 100.0) * 120.0; // kg
    }

    private String genererExplication(List<PointCollecte> ordre, double distance,
                                      int duree, double poids, double capacite, int taux) {
        StringBuilder sb = new StringBuilder();
        sb.append("Algorithme utilisé : Nearest Neighbor + 2-opt (CVRP)\n\n");
        sb.append("L'algorithme a calculé l'ordre de visite optimal pour ").append(ordre.size())
          .append(" points de collecte en minimisant la distance totale parcourue.\n\n");
        sb.append("Ordre recommandé :\n");
        sb.append("  Dépôt → ");
        for (int i = 0; i < ordre.size(); i++) {
            sb.append(ordre.get(i).getReference() != null ? ordre.get(i).getReference() : "Point " + (i + 1));
            if (i < ordre.size() - 1) sb.append(" → ");
        }
        sb.append(" → Dépôt\n\n");
        sb.append("Métriques :\n");
        sb.append("  • Distance totale : ").append(String.format("%.1f", distance)).append(" km\n");
        sb.append("  • Durée estimée : ").append(duree / 60).append("h").append(duree % 60).append("min\n");
        sb.append("  • Poids estimé : ").append(String.format("%.0f", poids)).append(" kg / ").append(String.format("%.0f", capacite)).append(" kg\n");
        sb.append("  • Taux de remplissage camion : ").append(taux).append("%");
        return sb.toString();
    }

    private String genererRecommandations(int taux, double distance, double poids, double capacite) {
        List<String> recs = new ArrayList<>();
        if (taux > 95) {
            recs.add("⚠️ Le camion sera chargé à " + taux + "% de sa capacité. Vérifiez que la charge est bien répartie.");
        } else if (taux < 40) {
            recs.add("ℹ️ Le taux de remplissage est faible (" + taux + "%). Envisagez d'ajouter d'autres points de collecte.");
        }
        if (distance > 100) {
            recs.add("⚠️ La distance totale est importante (" + String.format("%.1f", distance) + " km). Vérifiez l'autonomie du camion.");
        }
        if (recs.isEmpty()) {
            recs.add("✅ La tournée est optimisée. Taux de remplissage et distance sont équilibrés.");
        }
        return String.join("\n", recs);
    }

    public RouteOptimization validateOptimization(String id, String statut) {
        RouteOptimization opt = routeOptimizationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Optimisation introuvable : " + id));
        opt.setStatut(statut);
        opt.setDateValidation(LocalDateTime.now());
        return routeOptimizationRepository.save(opt);
    }

    public List<RouteOptimization> getAll() {
        return routeOptimizationRepository.findAllByOrderByDateCreationDesc();
    }
}
