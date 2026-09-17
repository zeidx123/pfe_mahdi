package com.municipalite.gestiontournees.ai.rag.service;

import java.util.List;

/**
 * Interface d'abstraction pour les services de génération d'embeddings vectoriels.
 * Permet de basculer facilement entre Gemini, OpenAI, Ollama, etc.
 */
public interface EmbeddingService {

    /**
     * Génère un vecteur d'embedding pour un texte donné.
     */
    List<Float> generateEmbedding(String text);

    /**
     * Génère une liste de vecteurs d'embeddings pour plusieurs textes.
     */
    List<List<Float>> generateEmbeddings(List<String> texts);
}
