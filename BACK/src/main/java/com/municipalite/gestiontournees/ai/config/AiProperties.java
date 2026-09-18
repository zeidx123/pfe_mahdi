package com.municipalite.gestiontournees.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriétés de configuration centralisées pour tous les agents IA.
 * Les valeurs sont lues depuis application.properties (préfixe "ai").
 */
@ConfigurationProperties(prefix = "ai")
@Data
public class AiProperties {

    private Gemini gemini = new Gemini();
    private Storage storage = new Storage();
    private Rag rag = new Rag();

    @Data
    public static class Gemini {
        private String apiKey;
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        private String modelText = "gemini-3.8-flash";
        private String modelVision = "gemini-3.8-flash";
        private int timeoutSeconds = 30;
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
        private String embeddingModel = "gemini-embedding-2";
        private int embeddingDimensions = 768;
        private String embeddingVersion = "text-v1";
    }
}
