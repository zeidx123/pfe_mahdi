package com.municipalite.gestiontournees.ai.rag.service;

import com.municipalite.gestiontournees.ai.rag.model.DocumentChunk;
import com.municipalite.gestiontournees.ai.rag.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implémentation du VectorStore s'appuyant sur MongoDB et une recherche hybride (Vectoriel Cosinus + Pertinence Textuelle BM25/Keywords).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MongoVectorStoreService implements VectorStoreService {

    private final DocumentChunkRepository documentChunkRepository;

    private static final Set<String> STOP_WORDS = Set.of(
            "le", "la", "les", "un", "une", "des", "du", "de", "d", "l", "et", "en", "ou", "a", "au", "aux",
            "est", "sont", "pour", "par", "dans", "sur", "avec", "que", "qui", "qu", "ce", "cette", "ces",
            "comment", "pourquoi", "quand", "quel", "quelle", "quels", "quelles", "doit", "dois", "doivent",
            "faire", "fait", "faut", "peut", "pouvez", "peuvent", "si", "mon", "ma", "mes", "ton", "ta", "tes",
            "son", "sa", "ses", "notre", "nos", "votre", "vos", "leur", "leurs", "tout", "tous", "toute", "toutes",
            "autre", "autres", "plus", "vers", "chez", "sous", "afin", "ainsi", "bien"
    );

    @Override
    public void saveChunk(DocumentChunk chunk) {
        documentChunkRepository.save(chunk);
    }

    @Override
    public void saveChunks(List<DocumentChunk> chunks) {
        documentChunkRepository.saveAll(chunks);
    }

    @Override
    public List<ScoredChunk> searchSimilar(String queryText, List<Float> queryEmbedding, int topK, double minSimilarityThreshold) {
        List<DocumentChunk> allChunks = documentChunkRepository.findAll();
        if (allChunks.isEmpty()) {
            log.warn("Aucun chunk présent en base de données RAG.");
            return List.of();
        }

        List<String> queryKeywords = extractKeywords(queryText);
        List<ScoredChunk> scoredChunks = new ArrayList<>();

        for (DocumentChunk chunk : allChunks) {
            double vectorScore = 0.0;
            if (queryEmbedding != null && chunk.getEmbedding() != null && !chunk.getEmbedding().isEmpty()) {
                vectorScore = calculateCosineSimilarity(queryEmbedding, chunk.getEmbedding());
            }

            double textScore = calculateTextKeywordScore(queryKeywords, chunk.getContent());

            // Score hybride pondéré : 40% Vectoriel + 60% Mots-clés textuels
            double hybridScore = (vectorScore * 0.40) + (textScore * 0.60);

            if (hybridScore >= minSimilarityThreshold) {
                scoredChunks.add(ScoredChunk.builder()
                        .chunk(chunk)
                        .score(hybridScore)
                        .build());
            }
        }

        // Trier par score hybride décroissant et prendre les topK
        List<ScoredChunk> results = scoredChunks.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());

        log.info("Recherche Hybride RAG: {} chunks candidats retenus sur {} (Threshold >= {}, Query='{}', Keywords={})",
                results.size(), allChunks.size(), minSimilarityThreshold, queryText, queryKeywords);

        return results;
    }

    @Override
    public void deleteByDocumentId(String documentId) {
        documentChunkRepository.deleteByDocumentId(documentId);
    }

    private List<String> extractKeywords(String text) {
        if (text == null) return List.of();
        String normalized = text.toLowerCase().replaceAll("[^a-z0-9àâçéèêëîïôûùüÿñæœ\\s]", " ");
        return Arrays.stream(normalized.split("\\s+"))
                .filter(w -> w.length() >= 3 && !STOP_WORDS.contains(w))
                .distinct()
                .collect(Collectors.toList());
    }

    private double calculateTextKeywordScore(List<String> keywords, String content) {
        if (keywords.isEmpty() || content == null || content.isEmpty()) {
            return 0.0;
        }

        String lowerContent = content.toLowerCase();
        int matches = 0;

        for (String kw : keywords) {
            if (lowerContent.contains(kw)) {
                matches++;
            } else if (kw.length() >= 5 && lowerContent.contains(kw.substring(0, kw.length() - 2))) {
                // Recherche par racine (ex: "endommag" pour "endommagé/endommagement")
                matches++;
            }
        }

        if (matches == 0) {
            return 0.0;
        }

        double ratio = (double) matches / keywords.size();
        // Boost de pertinence si au moins 1 mot-clé clé match
        return 0.35 + (0.65 * ratio);
    }

    private double calculateCosineSimilarity(List<Float> vecA, List<Float> vecB) {
        int size = Math.min(vecA.size(), vecB.size());
        if (size == 0) return 0.0;

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < size; i++) {
            float a = vecA.get(i);
            float b = vecB.get(i);

            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
