package com.municipalite.gestiontournees.ai.rag.service;

import com.fasterxml.jackson.databind.*;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.rag.model.*;
import com.municipalite.gestiontournees.ai.rag.repository.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runs authored seed retrieval cases. It does not call a model or claim human-reviewed accuracy. */
class RagSeedEvaluationTest {
    @Test void evaluateAuthoredLexicalSeed() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String externalFixture = System.getProperty("pfe.rag.fixture");
        JsonNode fixture = externalFixture == null
                ? mapper.readTree(getClass().getResourceAsStream("/pfe/rag-seed.json"))
                : mapper.readTree(Path.of(externalFixture).toFile());
        var chunksRepository = mock(DocumentChunkRepository.class);
        var documentsRepository = mock(AiDocumentRepository.class);
        AiProperties properties = new AiProperties();
        List<DocumentChunk> chunks = new ArrayList<>();
        List<AiDocument> documents = new ArrayList<>();
        for (JsonNode document : fixture.path("documents")) {
            String id = document.path("id").asText();
            chunks.add(DocumentChunk.builder().id(id).documentId(id).content(document.path("content").asText()).build());
            documents.add(AiDocument.builder().id(id).approved(true).status(AiDocument.DocumentStatus.TEXT_READY).build());
        }
        when(chunksRepository.findAll()).thenReturn(chunks);
        when(documentsRepository.findAll()).thenReturn(documents);
        MongoVectorStoreService store = new MongoVectorStoreService(chunksRepository, documentsRepository, properties);
        List<Map<String, Object>> rows = new ArrayList<>();
        double totalRecall = 0, reciprocalRank = 0;
        int answerable = 0, unanswerable = 0, emptyUnanswerable = 0;
        for (JsonNode question : fixture.path("questions")) {
            Set<String> relevant = new HashSet<>(); question.path("relevantIds").forEach(id -> relevant.add(id.asText()));
            long start = System.nanoTime();
            List<String> retrieved = store.search(question.path("question").asText(), null, 3, .7, VectorStoreService.RetrievalMode.LEXICAL)
                    .stream().map(r -> r.getChunk().getId()).toList();
            double latencyMs = (System.nanoTime() - start) / 1_000_000.0;
            if (!relevant.isEmpty()) {
                answerable++;
                totalRecall += retrieved.stream().filter(relevant::contains).count() / (double) relevant.size();
                for (int i = 0; i < retrieved.size(); i++) if (relevant.contains(retrieved.get(i))) { reciprocalRank += 1.0 / (i + 1); break; }
            } else { unanswerable++; if (retrieved.isEmpty()) emptyUnanswerable++; }
            rows.add(Map.of("questionId", question.path("id").asText(), "relevantIds", relevant,
                    "retrievedIds", retrieved, "latencyMs", latencyMs));
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("fixtureVersion", fixture.path("version").asText());
        report.put("reviewStatus", fixture.path("reviewStatus").asText("UNREVIEWED"));
        report.put("mode", "LEXICAL_BM25"); report.put("providerCalls", 0);
        report.put("questionCount", rows.size()); report.put("answerableCount", answerable);
        report.put("recallAt3", answerable == 0 ? null : totalRecall / answerable);
        report.put("mrrAt3", answerable == 0 ? null : reciprocalRank / answerable);
        report.put("unanswerableCount", unanswerable); report.put("unanswerableEmptyRetrievalCount", emptyUnanswerable);
        report.put("semanticAndHybridEvaluation", "NOT_RUN: compatible real document/query embeddings are required");
        report.put("answerGroundingAndCitationSupport", "NOT_RUN: generation and independent manual review are required");
        report.put("rows", rows);
        Path output = Path.of("target/pfe-evaluation/rag-lexical-seed-results.json");
        Files.createDirectories(output.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        assertThat(rows).isNotEmpty();
        assertThat(totalRecall).isBetween(0.0, (double) answerable);
    }
}
