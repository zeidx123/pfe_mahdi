package com.municipalite.gestiontournees.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propriétés de configuration centralisées pour tous les agents IA.
 * Les valeurs sont lues depuis application.properties (préfixe "ai").
 */
@Configuration
@ConfigurationProperties(prefix = "ai")
@Data
public class AiProperties {

    private Gemini gemini = new Gemini();
    private Cv cv = new Cv();
    private Route route = new Route();
    private Vision vision = new Vision();
    private Storage storage = new Storage();
    private Rag rag = new Rag();

    @Data
    public static class Gemini {
        private String apiKey;
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        private String modelText = "gemini-1.5-flash";
        private String modelVision = "gemini-1.5-flash";
        private int timeoutSeconds = 60;
    }

    @Data
    public static class Cv {
        private int minExperienceYears = 2;
        private String requiredPermisTypes = "B,C,CE";
        private int scoreThresholdRecommended = 70;
        private int scoreThresholdExaminer = 40;
    }

    @Data
    public static class Route {
        private double depotLatitude = 33.5731;
        private double depotLongitude = -7.5898;
        private double defaultSpeedKmh = 40.0;
        private double fuelConsumptionPer100km = 20.0;
    }

    @Data
    public static class Vision {
        private double confidenceThresholdAnomaly = 0.65;
        private boolean autoCreateProblem = true;
    }

    @Data
    public static class Storage {
        private String cvDir = "uploads/cv";
        private String photosDir = "uploads/photos";
        private String documentsDir = "uploads/documents";
    }

    @Data
    public static class Rag {
        private int topK = 5;
        private double similarityThreshold = 0.70;
        private int chunkSize = 1000;
        private int chunkOverlap = 150;
        private String embeddingModel = "text-embedding-004";
    }
}
