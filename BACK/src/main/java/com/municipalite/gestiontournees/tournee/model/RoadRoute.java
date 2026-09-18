package com.municipalite.gestiontournees.tournee.model;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RoadRoute {
    @Data @AllArgsConstructor @NoArgsConstructor
    public static class Depot { private double latitude; private double longitude; private String label; }
    @Data @AllArgsConstructor @NoArgsConstructor
    public static class Leg { private String fromId; private String toId; private double distanceMeters; private double durationSeconds; }
    @Data @AllArgsConstructor @NoArgsConstructor
    public static class Comparison {
        private String algorithm;
        private List<String> orderedPointIds;
        private double distanceMeters;
        private double travelSeconds;
        private double runtimeMillis;
    }
    private Depot depot;
    private String provider;
    private String profile;
    private String algorithm;
    private String algorithmVersion;
    private LocalDateTime calculatedAt;
    private List<String> orderedPointIds;
    /** GeoJSON coordinate order: longitude, latitude. */
    private List<List<Double>> geometry;
    private List<Leg> legs;
    private double distanceMeters;
    private double travelSeconds;
    private double serviceSeconds;
    private double totalSeconds;
    private List<Comparison> comparisons;
    /** Depot first, followed by the supervisor's original manual point order. */
    private List<String> matrixPointIds;
    private List<List<Double>> matrixCoordinates;
    private double[][] durations;
    private double[][] distances;
}
