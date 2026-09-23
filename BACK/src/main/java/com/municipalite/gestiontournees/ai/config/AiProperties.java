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
    private OpenRouter openRouter = new OpenRouter();
    private Groq groq = new Groq();
    private CollectionProvider collectionProvider = CollectionProvider.GROQ;
    private Storage storage = new Storage();
    private Rag rag = new Rag();

    public enum CollectionProvider { GROQ, OPENROUTER }

    public String getCollectionTextModel() {
        return collectionProvider == CollectionProvider.GROQ ? groq.getModelText() : openRouter.getModelText();
    }

    public String getCollectionVisionModel() {
        return collectionProvider == CollectionProvider.GROQ ? groq.getModelVision() : openRouter.getModelVision();
    }

    @Data
    public static class Groq {
        private String apiKey;
        private String baseUrl = "https://api.groq.com/openai/v1";
        private String modelText = "qwen/qwen3.8-27b";
        private String modelVision = "qwen/qwen3.8-27b";
        private int timeoutSeconds = 60;
    }

    @Data
    public static class Gemini {
        private String apiKey;
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        private String modelText = "gemini-3.8-flash";
        private int timeoutSeconds = 30;
    }

    @Data
    public static class OpenRouter {
        private String apiKey;
        private String baseUrl = "https://openrouter.ai/api/v1";
        private String modelText = "qwen/qwen3.8-27b:free";
        private String modelVision = "qwen/qwen3.8-27b:free";
        private int timeoutSeconds = 60;
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
