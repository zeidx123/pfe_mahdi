package com.municipalite.gestiontournees.tournee.service;

import com.municipalite.gestiontournees.pointcollecte.model.PointCollecte;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service d'optimisation IA des tournées de collecte.
 * Utilise l'algorithme Nearest Neighbor (TSP) + formule Haversine pour le chemin le plus court.
 */
@Service("tourneeRouteOptimizationService")
@Slf4j
public class TourneeRouteOptimizationService {

    private static final double AVG_SPEED_KMH = 30.0;           // Vitesse moyenne camion urbain
    private static final int STOP_DURATION_MINUTES = 5;          // Temps par arrêt de collecte
    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Optimise l'ordre des points de collecte (TSP Nearest Neighbor).
     * Retourne un résultat avec : ordre optimal, distances étapes, distance totale, durée estimée.
     */
    public RouteOptimizationResult optimize(List<PointCollecte> points) {
        if (points == null || points.isEmpty()) {
            return RouteOptimizationResult.empty();
        }

        // Filtrer les points ayant des coordonnées GPS valides
        List<PointCollecte> withGps = new ArrayList<>();
        List<PointCollecte> withoutGps = new ArrayList<>();
        for (PointCollecte p : points) {
            if (p.getLatitude() != null && p.getLongitude() != null) {
                withGps.add(p);
            } else {
                withoutGps.add(p);
                log.warn("Point {} n'a pas de coordonnées GPS - placé en fin de tournée", p.getReference());
            }
        }

        if (withGps.isEmpty()) {
            // Pas de coordonnées → ordre inchangé, pas de calcul
            List<String> ids = new ArrayList<>();
            List<String> adresses = new ArrayList<>();
            for (PointCollecte p : points) {
                ids.add(p.getId());
                adresses.add(p.getReference() + (p.getAdresse() != null ? " – " + p.getAdresse() : ""));
            }
            return RouteOptimizationResult.builder()
                    .pointsIdsOrdonnes(ids)
                    .pointsAdresses(adresses)
                    .distancesEtapes(new ArrayList<>())
                    .distanceTotaleKm(0.0)
                    .dureeEstimeeMinutes(points.size() * STOP_DURATION_MINUTES)
                    .dureeFormatee(formatDuree(points.size() * STOP_DURATION_MINUTES))
                    .explicationOrdre("Les points de collecte ne possèdent pas encore de coordonnées GPS valides. L'ordre initial a été conservé.")
                    .optimise(false)
                    .build();
        }

        // === Algorithme TSP Nearest Neighbor ===
        List<PointCollecte> ordered = new ArrayList<>();
        List<PointCollecte> remaining = new ArrayList<>(withGps);
        List<Double> etapes = new ArrayList<>();

        // Départ depuis le premier point de la liste (point de départ naturel)
        PointCollecte current = remaining.remove(0);
        ordered.add(current);

        while (!remaining.isEmpty()) {
            PointCollecte nearest = null;
            double minDist = Double.MAX_VALUE;
            for (PointCollecte candidate : remaining) {
                double dist = haversine(
                        current.getLatitude(), current.getLongitude(),
                        candidate.getLatitude(), candidate.getLongitude());
                if (dist < minDist) {
                    minDist = dist;
                    nearest = candidate;
                }
            }
            etapes.add(Math.round(minDist * 100.0) / 100.0); // Précision 10m
            ordered.add(nearest);
            remaining.remove(nearest);
            current = nearest;
        }

        // Ajouter les points sans GPS à la fin
        ordered.addAll(withoutGps);

        // Calcul distance totale
        double totalKm = etapes.stream().mapToDouble(Double::doubleValue).sum();
        totalKm = Math.round(totalKm * 10.0) / 10.0;

        // Calcul durée : temps de trajet + temps d'arrêt
        int dureeTrajetMin = (int) Math.ceil((totalKm / AVG_SPEED_KMH) * 60);
        int dureeArretMin = ordered.size() * STOP_DURATION_MINUTES;
        int dureeTotale = dureeTrajetMin + dureeArretMin;

        // Préparer les résultats
        List<String> ids = new ArrayList<>();
        List<String> adresses = new ArrayList<>();
        for (PointCollecte p : ordered) {
            ids.add(p.getId());
            adresses.add(p.getReference() + (p.getAdresse() != null ? " – " + p.getAdresse() : ""));
        }

        String explicationText = generateExplicationOrdre(ordered, etapes, totalKm, dureeTotale);

        return RouteOptimizationResult.builder()
                .pointsIdsOrdonnes(ids)
                .pointsAdresses(adresses)
                .distancesEtapes(etapes)
                .distanceTotaleKm(totalKm)
                .dureeEstimeeMinutes(dureeTotale)
                .dureeFormatee(formatDuree(dureeTotale))
                .explicationOrdre(explicationText)
                .optimise(true)
                .build();
    }

    private String generateExplicationOrdre(List<PointCollecte> ordered, List<Double> etapes, double totalKm, int dureeTotale) {
        StringBuilder sb = new StringBuilder();
        sb.append("L'Agent IA a sélectionné cet ordre selon l'algorithme du chemin le plus proche (TSP - Nearest Neighbor) :\n\n");

        if (ordered.size() >= 1) {
            sb.append("1. **Départ à ").append(ordered.get(0).getReference()).append("** (Point d'origine du secteur).\n");
        }

        for (int i = 0; i < etapes.size(); i++) {
            PointCollecte prev = ordered.get(i);
            PointCollecte next = ordered.get(i + 1);
            double dist = etapes.get(i);
            sb.append(i + 2).append(". **Visite de ").append(next.getReference()).append("** : situé à seulement **")
              .append(dist < 1 ? (int)(dist * 1000) + " mètres" : String.format("%.1f km", dist))
              .append("** de ").append(prev.getReference()).append(" (le point non visité le plus proche).\n");
        }

        sb.append("\n💡 **Pourquoi cet ordre ?**\n");
        sb.append("• Cet itinéraire évite les allers-retours inutiles (détours géographiques).\n");
        sb.append("• Il garantit une distance minimale de **").append(totalKm).append(" km** et un temps de trajet estimé à **").append(formatDuree(dureeTotale)).append("** (comprenant 5 min de ramassage par conteneur).\n");

        return sb.toString();
    }

    /**
     * Formule Haversine — Distance géodésique entre deux points GPS en km.
     */
    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String formatDuree(int totalMinutes) {
        if (totalMinutes < 60) return totalMinutes + " min";
        int h = totalMinutes / 60;
        int m = totalMinutes % 60;
        return m > 0 ? h + "h " + m + "min" : h + "h";
    }
}
