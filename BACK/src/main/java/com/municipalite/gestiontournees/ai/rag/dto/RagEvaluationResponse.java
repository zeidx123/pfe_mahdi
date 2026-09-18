package com.municipalite.gestiontournees.ai.rag.dto;

import java.util.List;

public record RagEvaluationResponse(String question, String retrievalQuery, CorpusInfo corpus, Settings settings,
                                    String embeddingStatus, double embeddingLatencyMs, List<ModeResult> results) {
    public record CorpusInfo(String fingerprint, int documentCount, int chunkCount, int compatibleChunkCount) {}
    public record Settings(String evaluationVersion, int topK, double similarityThreshold,
                           double bm25K1, double bm25B, int reciprocalRankFusionK,
                           String provider, String generationModel, String promptVersion,
                           String embeddingModel, int embeddingDimensions, String embeddingVersion) {}
    public record CorpusChunk(SourceDto source, String contentSha256, boolean compatibleEmbedding) {}
    public record CorpusResponse(CorpusInfo corpus, Settings settings, List<CorpusChunk> chunks) {}
    public record ModeResult(String requestedMode, String effectiveMode, String retrievalStatus,
                             List<SourceDto> retrievedSources, double retrievalLatencyMs,
                             String generationStatus, String status, String answer, List<SourceDto> citations,
                             double generationLatencyMs, double totalLatencyMs) {}
}
