package com.municipalite.gestiontournees.ai.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.common.AiServiceException;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.rag.dto.*;
import com.municipalite.gestiontournees.ai.rag.dto.RagEvaluationResponse.*;
import com.municipalite.gestiontournees.ai.rag.model.*;
import com.municipalite.gestiontournees.ai.rag.service.VectorStoreService.*;
import com.municipalite.gestiontournees.security.AccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Bounded supervisor experiment: one approved corpus and one query embedding per comparison. */
@Service
@RequiredArgsConstructor
public class RagEvaluationService {
    private final AccessService access;
    private final MongoVectorStoreService store;
    private final EmbeddingService embeddings;
    private final RagService rag;
    private final AiProperties properties;
    private final ObjectMapper mapper;

    public CorpusResponse corpus() {
        access.requireSupervisor();
        List<DocumentChunk> chunks = store.approvedCorpus();
        return new CorpusResponse(info(chunks), settings(), chunks.stream().map(chunk -> new CorpusChunk(
                rag.toSource(new ScoredChunk(chunk, 0), chunk.getContent()), sha256(chunk.getContent()), compatible(chunk))).toList());
    }

    public RagEvaluationResponse evaluate(RagEvaluationRequest request) {
        access.requireSupervisor(); // Also protects direct service calls, before text or providers are accessed.
        if (request.getModes() == null || request.getModes().isEmpty()
                || new HashSet<>(request.getModes()).size() != request.getModes().size()
                || request.getModes().stream().anyMatch(Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modes distincts requis.");
        }
        List<DocumentChunk> corpus = store.approvedCorpus();
        CorpusInfo corpusInfo = info(corpus);
        if (request.getExpectedCorpusFingerprint() != null
                && !request.getExpectedCorpusFingerprint().equals(corpusInfo.fingerprint())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le corpus a changé. Recommencez l'expérience avec un nouvel export.");
        }
        RagConversation history = RagConversation.builder().messages(request.getHistory().stream()
                .map(turn -> RagMessage.builder().sender(turn.sender()).content(turn.content()).build()).toList()).build();
        String question = request.getQuestion().trim();
        String query = rag.retrievalQuery(question, history);
        List<Float> queryEmbedding = null;
        String embeddingStatus = "NOT_REQUESTED";
        double embeddingMs = 0;
        if (request.getModes().stream().anyMatch(mode -> mode != RetrievalMode.LEXICAL)) {
            if (corpusInfo.compatibleChunkCount() == 0) embeddingStatus = "INDEX_NOT_READY";
            else {
                long start = System.nanoTime();
                try {
                    queryEmbedding = embeddings.generateEmbedding(query);
                    if (queryEmbedding == null || queryEmbedding.size() != properties.getRag().getEmbeddingDimensions()
                            || MongoVectorStoreService.cosine(queryEmbedding, queryEmbedding) == 0) {
                        throw new AiServiceException("Embedding non compatible.");
                    }
                    embeddingStatus = "AVAILABLE";
                } catch (AiServiceException e) { queryEmbedding = null; embeddingStatus = "PROVIDER_UNAVAILABLE"; }
                embeddingMs = elapsed(start);
            }
        }
        List<ModeResult> results = new ArrayList<>();
        for (RetrievalMode mode : request.getModes()) {
            String retrievalStatus = corpus.isEmpty() ? "EMPTY_CORPUS" : mode == RetrievalMode.LEXICAL ? "AVAILABLE"
                    : !"AVAILABLE".equals(embeddingStatus) ? embeddingStatus
                    : corpusInfo.compatibleChunkCount() < corpusInfo.chunkCount() ? "INDEX_INCOMPLETE" : "AVAILABLE";
            String effective = mode == RetrievalMode.LEXICAL ? "LEXICAL" : queryEmbedding == null
                    ? (mode == RetrievalMode.HYBRID ? "LEXICAL" : "UNAVAILABLE") : mode.name();
            long retrievalStart = System.nanoTime();
            List<ScoredChunk> retrieved = store.searchCorpus(corpus, query, queryEmbedding,
                    properties.getRag().getTopK(), properties.getRag().getSimilarityThreshold(), mode);
            double retrievalMs = elapsed(retrievalStart);
            List<SourceDto> sources = retrieved.stream().map(chunk -> rag.toSource(chunk, chunk.getChunk().getContent())).toList();
            String status = null, answer = null, generationStatus = "NOT_REQUESTED";
            List<SourceDto> citations = List.of();
            double generationMs = 0;
            if (request.isGenerateAnswer()) {
                long generationStart = System.nanoTime();
                if (retrieved.isEmpty() && !"AVAILABLE".equals(retrievalStatus) && !"EMPTY_CORPUS".equals(retrievalStatus)) {
                    status = "PROVIDER_UNAVAILABLE";
                    answer = "Aucun résultat exploitable : fournisseur ou index sémantique indisponible.";
                    generationStatus = "SKIPPED_RETRIEVAL_UNAVAILABLE";
                } else {
                    RagService.GroundedAnswer generated = rag.answerFromContext(question, retrieved, history, true);
                    status = generated.status(); answer = generated.answer(); citations = generated.sources();
                    generationStatus = generated.generationStatus();
                }
                generationMs = elapsed(generationStart);
            }
            results.add(new ModeResult(mode.name(), effective, retrievalStatus, sources, retrievalMs, generationStatus,
                    status, answer, citations, generationMs, retrievalMs + generationMs + (mode == RetrievalMode.LEXICAL ? 0 : embeddingMs)));
        }
        return new RagEvaluationResponse(question, query, corpusInfo, settings(), embeddingStatus, embeddingMs, results);
    }

    private Settings settings() {
        var config = properties.getRag();
        return new Settings("rag-comparison-v1", config.getTopK(), config.getSimilarityThreshold(), 1.2, .75, 60,
                config.getProvider().name(), config.getProvider() == AiProperties.RagGenerationProvider.GROQ
                    ? properties.getGroq().getModelText() : properties.getGemini().getModelText(), RagService.PROMPT_VERSION,
                config.getEmbeddingModel(), config.getEmbeddingDimensions(), config.getEmbeddingVersion());
    }

    private boolean compatible(DocumentChunk chunk) {
        return chunk.getEmbedding() != null && store.compatibleEmbedding(chunk, chunk.getEmbedding())
                && MongoVectorStoreService.cosine(chunk.getEmbedding(), chunk.getEmbedding()) > 0;
    }

    private CorpusInfo info(List<DocumentChunk> chunks) {
        try {
            // Hash text, references and actual vectors so equal IDs alone cannot disguise reindexing.
            List<Map<String, Object>> fingerprintInput = chunks.stream().map(chunk -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("source", rag.toSource(new ScoredChunk(chunk, 0), chunk.getContent()));
                item.put("embeddingModel", chunk.getEmbeddingModel()); item.put("embeddingVersion", chunk.getEmbeddingVersion());
                item.put("embeddingDimensions", chunk.getEmbeddingDimensions()); item.put("embedding", chunk.getEmbedding());
                return item;
            }).toList();
            return new CorpusInfo(sha256(mapper.writeValueAsString(fingerprintInput)),
                    (int) chunks.stream().map(DocumentChunk::getDocumentId).distinct().count(), chunks.size(),
                    (int) chunks.stream().filter(this::compatible).count());
        } catch (Exception e) { throw new IllegalStateException("Impossible de décrire le corpus d'évaluation.", e); }
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static double elapsed(long start) { return (System.nanoTime() - start) / 1_000_000.0; }
}
