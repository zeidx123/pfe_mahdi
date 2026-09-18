package com.municipalite.gestiontournees.tournee.service;

import com.municipalite.gestiontournees.pointcollecte.model.PointCollecte;
import com.municipalite.gestiontournees.tournee.model.RoadRoute;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

/** Deterministic routing over a directed road matrix. No straight-line fallback. */
@Service @RequiredArgsConstructor
public class TourneeRouteOptimizationService {
    private final RoutingProperties properties;
    private final OpenRouteServiceClient roads;

    public RoadRoute optimize(List<PointCollecte> points) {
        validateCoordinate(properties.getDepotLatitude(), properties.getDepotLongitude(), "dépôt configuré");
        if (points == null || points.isEmpty() || points.size() > properties.getMaxPoints())
            throw new IllegalArgumentException("La tournée doit contenir de 1 à " + properties.getMaxPoints() + " points");
        if (properties.getServiceSeconds() < 0) throw new IllegalArgumentException("Durée de service invalide");
        List<List<Double>> coordinates = new ArrayList<>();
        coordinates.add(List.of(properties.getDepotLongitude(), properties.getDepotLatitude()));
        List<String> ids = new ArrayList<>(List.of("DEPOT"));
        for (PointCollecte point : points) {
            validateCoordinate(point.getLatitude(), point.getLongitude(), point.getReference());
            coordinates.add(List.of(point.getLongitude(), point.getLatitude())); ids.add(point.getId());
        }
        OpenRouteServiceClient.Matrix matrix = roads.matrix(coordinates);
        List<RouteAlgorithms.Result> compared = RouteAlgorithms.compare(matrix.durations(), matrix.distances());
        RouteAlgorithms.Result selected = RouteAlgorithms.bestHeuristic(compared);
        List<Integer> order = selected.order();
        List<RoadRoute.Leg> legs = new ArrayList<>();
        List<List<Double>> routeCoordinates = new ArrayList<>(List.of(coordinates.get(0)));
        int from = 0;
        for (int next : order) {
            legs.add(new RoadRoute.Leg(ids.get(from), ids.get(next), matrix.distances()[from][next], matrix.durations()[from][next]));
            routeCoordinates.add(coordinates.get(next)); from = next;
        }
        legs.add(new RoadRoute.Leg(ids.get(from), "DEPOT", matrix.distances()[from][0], matrix.durations()[from][0]));
        routeCoordinates.add(coordinates.get(0));
        double duration = legs.stream().mapToDouble(RoadRoute.Leg::getDurationSeconds).sum();
        double distance = legs.stream().mapToDouble(RoadRoute.Leg::getDistanceMeters).sum();
        List<String> pointIds = order.stream().map(ids::get).toList();
        double service = points.size() * (double) properties.getServiceSeconds();
        return RoadRoute.builder().depot(new RoadRoute.Depot(properties.getDepotLatitude(), properties.getDepotLongitude(), properties.getDepotLabel()))
                .provider("OpenRouteService").profile(properties.getProfile()).algorithm(selected.algorithm()).algorithmVersion("road-v1")
                .calculatedAt(LocalDateTime.now()).orderedPointIds(pointIds).geometry(roads.geometry(routeCoordinates))
                .legs(legs).distanceMeters(distance).travelSeconds(duration).serviceSeconds(service).totalSeconds(duration + service)
                .comparisons(compared.stream().map(r -> new RoadRoute.Comparison(r.algorithm(), r.order().stream().map(ids::get).toList(),
                        r.cost().meters(), r.cost().seconds(), r.runtimeMillis())).toList())
                .matrixPointIds(ids).matrixCoordinates(coordinates).durations(matrix.durations()).distances(matrix.distances()).build();
    }

    public static void validateCoordinate(Double latitude, Double longitude, String label) {
        if (latitude == null || longitude == null || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)
            throw new IllegalArgumentException("Coordonnées GPS invalides ou absentes : " + label);
    }
}
