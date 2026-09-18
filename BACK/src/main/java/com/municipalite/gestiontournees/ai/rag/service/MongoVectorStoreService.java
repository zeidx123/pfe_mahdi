package com.municipalite.gestiontournees.ai.rag.service;

import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.rag.model.AiDocument;
import com.municipalite.gestiontournees.ai.rag.model.DocumentChunk;
import com.municipalite.gestiontournees.ai.rag.repository.AiDocumentRepository;
import com.municipalite.gestiontournees.ai.rag.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/** Small-corpus BM25/cosine retrieval. No external vector database is needed for this PFE. */
@Service
@RequiredArgsConstructor
public class MongoVectorStoreService implements VectorStoreService {
    private final DocumentChunkRepository documentChunkRepository;
    private final AiDocumentRepository aiDocumentRepository;
    private final AiProperties aiProperties;
    private static final double K1 = 1.2;
    private static final double B = 0.75;
    private static final Set<String> STOP_WORDS = Set.of("le", "la", "les", "un", "une", "des", "du", "de", "d", "l", "et", "en", "ou", "a", "au", "aux",
            "est", "sont", "pour", "par", "dans", "sur", "avec", "que", "qui", "qu", "ce", "cette", "ces", "comment", "pourquoi", "quand",
            "quel", "quelle", "quels", "quelles", "doit", "dois", "doivent", "faire", "fait", "faut", "peut", "pouvez", "peuvent", "si",
            "mon", "ma", "mes", "ton", "ta", "tes", "son", "sa", "ses", "notre", "nos", "votre", "vos", "leur", "leurs", "tout", "tous",
            "toute", "toutes", "autre", "autres", "plus", "vers", "chez", "sous", "afin", "ainsi", "bien", "je", "il", "elle", "nous", "vous");

    @Override public void saveChunk(DocumentChunk chunk) { documentChunkRepository.save(chunk); }
    @Override public void saveChunks(List<DocumentChunk> chunks) { documentChunkRepository.saveAll(chunks); }
    @Override public void deleteByDocumentId(String id) { documentChunkRepository.deleteByDocumentId(id); }
    @Override public int usableChunkCount(String id) {
        return (int) documentChunkRepository.findByDocumentId(id).stream()
                .filter(chunk -> chunk.getContent() != null && !chunk.getContent().isBlank()).count();
    }

    @Override
    public List<ScoredChunk> searchSimilar(String text, List<Float> embedding, int topK, double threshold) {
        return search(text, embedding, topK, threshold, embedding == null ? RetrievalMode.LEXICAL : RetrievalMode.HYBRID);
    }

    /** Exposed for reproducible lexical/semantic/hybrid experiments using the same corpus. */
    public List<ScoredChunk> search(String text, List<Float> embedding, int topK, double threshold, RetrievalMode mode) {
        return searchCorpus(approvedCorpus(), text, embedding, topK, threshold, mode);
    }

    /** One approved text snapshot can be reused across every method in an evaluation request. */
    List<DocumentChunk> approvedCorpus() {
        Set<String> approvedIds = aiDocumentRepository.findAll().stream()
                .filter(AiDocument::isApproved)
                .filter(d -> d.getStatus() == AiDocument.DocumentStatus.INDEXED || d.getStatus() == AiDocument.DocumentStatus.TEXT_READY)
                .map(AiDocument::getId).collect(Collectors.toSet());
        return documentChunkRepository.findAll().stream()
                .filter(c -> approvedIds.contains(c.getDocumentId()) && c.getId() != null && c.getContent() != null && !c.getContent().isBlank())
                .sorted(Comparator.comparing(DocumentChunk::getId)).toList();
    }

    List<ScoredChunk> searchCorpus(List<DocumentChunk> corpus, String text, List<Float> embedding,
                                  int topK, double threshold, RetrievalMode mode) {
        if (topK <= 0 || corpus.isEmpty()) return List.of();
        Map<String, List<String>> tokens = new HashMap<>();
        for (DocumentChunk chunk : corpus) tokens.put(chunk.getId(), tokenize(chunk.getContent()));
        double averageLength = tokens.values().stream().mapToInt(List::size).average().orElse(1);
        if (averageLength == 0) averageLength = 1;
        Set<String> queryTerms = new HashSet<>(tokenize(text));
        Map<String, Long> documentFrequency = new HashMap<>();
        for (String term : queryTerms) documentFrequency.put(term, tokens.values().stream().filter(t -> t.contains(term)).count());
        Map<String, Double> lexical = new HashMap<>();
        Map<String, Double> semantic = new HashMap<>();
        for (DocumentChunk chunk : corpus) {
            if (mode != RetrievalMode.SEMANTIC) {
                List<String> terms = tokens.get(chunk.getId());
                double score = 0;
                for (String term : queryTerms) {
                    long frequency = terms.stream().filter(term::equals).count();
                    if (frequency == 0) continue;
                    double idf = Math.log(1 + (corpus.size() - documentFrequency.get(term) + .5) / (documentFrequency.get(term) + .5));
                    score += idf * frequency * (K1 + 1) / (frequency + K1 * (1 - B + B * terms.size() / averageLength));
                }
                if (score > 0) lexical.put(chunk.getId(), score);
            }
            if (mode != RetrievalMode.LEXICAL && embedding != null && compatibleEmbedding(chunk, embedding)) {
                double score = cosine(embedding, chunk.getEmbedding());
                if (score >= threshold && score > 0) semantic.put(chunk.getId(), score);
            }
        }
        Map<String, Integer> lexicalRanks = ranks(lexical);
        Map<String, Integer> semanticRanks = ranks(semantic);
        List<ScoredChunk> results = new ArrayList<>();
        for (DocumentChunk chunk : corpus) {
            String id = chunk.getId();
            if (!lexical.containsKey(id) && !semantic.containsKey(id)) continue;
            double score;
            if (mode == RetrievalMode.LEXICAL) score = lexical.get(id);
            else if (mode == RetrievalMode.SEMANTIC) score = semantic.get(id);
            else score = (lexicalRanks.containsKey(id) ? 1.0 / (60 + lexicalRanks.get(id)) : 0)
                    + (semanticRanks.containsKey(id) ? 1.0 / (60 + semanticRanks.get(id)) : 0);
            String used = lexical.containsKey(id) && semantic.containsKey(id) ? "HYBRID" : lexical.containsKey(id) ? "LEXICAL" : "SEMANTIC";
            results.add(new ScoredChunk(chunk, score, used));
        }
        return results.stream().sorted(Comparator.comparingDouble(ScoredChunk::getScore).reversed().thenComparing(c -> c.getChunk().getId())).limit(topK).toList();
    }

    private Map<String, Integer> ranks(Map<String, Double> scores) {
        List<String> ids = scores.keySet().stream().sorted(Comparator.<String>comparingDouble(scores::get).reversed().thenComparing(String::compareTo)).toList();
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) result.put(ids.get(i), i + 1);
        return result;
    }

    static List<String> tokenize(String text) {
        if (text == null) return List.of();
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[^a-z0-9]+", " ");
        return Arrays.stream(normalized.split("\\s+")).filter(w -> w.length() > 1 && !STOP_WORDS.contains(w)).toList();
    }

    boolean compatibleEmbedding(DocumentChunk chunk, List<Float> query) {
        return chunk.getEmbedding() != null && query.size() == aiProperties.getRag().getEmbeddingDimensions()
                && chunk.getEmbedding().size() == query.size()
                && Objects.equals(chunk.getEmbeddingModel(), aiProperties.getRag().getEmbeddingModel())
                && Objects.equals(chunk.getEmbeddingVersion(), aiProperties.getRag().getEmbeddingVersion())
                && Objects.equals(chunk.getEmbeddingDimensions(), aiProperties.getRag().getEmbeddingDimensions());
    }

    static double cosine(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.isEmpty() || a.size() != b.size()) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i) == null || b.get(i) == null || !Float.isFinite(a.get(i)) || !Float.isFinite(b.get(i))) return 0;
            dot += a.get(i) * (double) b.get(i); normA += a.get(i) * (double) a.get(i); normB += b.get(i) * (double) b.get(i);
        }
        return normA == 0 || normB == 0 ? 0 : dot / Math.sqrt(normA * normB);
    }
}
