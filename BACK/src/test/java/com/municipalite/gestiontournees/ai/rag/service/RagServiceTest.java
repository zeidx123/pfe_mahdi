package com.municipalite.gestiontournees.ai.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipalite.gestiontournees.ai.assistant.service.TunisianDialectService;
import com.municipalite.gestiontournees.ai.common.*;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.rag.dto.*;
import com.municipalite.gestiontournees.ai.rag.model.*;
import com.municipalite.gestiontournees.ai.rag.repository.RagConversationRepository;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagServiceTest {
    EmbeddingService embeddings = mock(EmbeddingService.class);
    VectorStoreService store = mock(VectorStoreService.class);
    RagGenerationClient generation = mock(RagGenerationClient.class);
    RagConversationRepository histories = mock(RagConversationRepository.class);
    TunisianDialectService dialectService = new TunisianDialectService();
    RagService service = new RagService(embeddings, store, generation, histories, new AiProperties(), new ObjectMapper(), dialectService);
    DocumentChunk chunk = DocumentChunk.builder().id("source-1").documentId("doc-1").documentName("Procédure").pageNumber(2)
            .sectionTitle("Accès").content("Si le point est inaccessible, consulter le superviseur.").build();

    void retrievedSource() {
        when(store.searchSimilar(anyString(), any(), anyInt(), anyDouble())).thenReturn(List.of(new VectorStoreService.ScoredChunk(chunk, .8)));
    }

    @Test void embeddingFailureReturnsLiteralExcerptsWithoutInventingProcedure() {
        when(embeddings.generateEmbedding(anyString())).thenThrow(new AiServiceException("Unavailable"));
        retrievedSource();
        RagResponse response = service.query(new RagQueryRequest(null, "Point inaccessible ?"), "driver");
        assertThat(response.getStatus()).isEqualTo("SOURCE_EXCERPTS");
        assertThat(response.getAnswer()).contains(chunk.getContent()).doesNotContain("police", "plaque");
        assertThat(response.getSources().get(0).getSnippet()).isEqualTo(chunk.getContent());
        assertThat(response.getConversationId()).isNotBlank();
        verify(generation).generateJson(anyString(), anyMap());
    }

    @Test void unavailableWithoutSourcesReturnsUnavailableAndPersistentConversationId() {
        when(embeddings.generateEmbedding(anyString())).thenThrow(new AiServiceException("Unavailable"));
        when(store.searchSimilar(anyString(), any(), anyInt(), anyDouble())).thenReturn(List.of());
        RagResponse response = service.query(new RagQueryRequest(null, "Point inaccessible ?"), "driver");
        assertThat(response.getStatus()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(response.getConversationId()).isNotBlank();
        verify(histories).save(any());
    }

    @Test void foreignConversationRejectedBeforeAnyProviderCall() {
        when(histories.findById("other")).thenReturn(Optional.of(RagConversation.builder().id("other").userId("someone-else").build()));
        assertThatThrownBy(() -> service.query(new RagQueryRequest("other", "Question"), "driver"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        verifyNoInteractions(embeddings, store, generation);
        verify(histories, never()).save(any());
    }

    @Test void inventedCitationFallsBackToExistingExcerpts() {
        when(embeddings.generateEmbedding(anyString())).thenReturn(List.of(1f));
        retrievedSource();
        when(generation.generateJson(anyString(), anyMap())).thenReturn("{\"status\":\"ANSWERED\",\"answer\":\"Contacter la police\",\"citations\":[{\"chunkId\":\"source-1\",\"quote\":\"police\"}]}");
        RagResponse response = service.query(new RagQueryRequest(null, "Point inaccessible ?"), "driver");
        assertThat(response.getStatus()).isEqualTo("SOURCE_EXCERPTS");
        assertThat(response.getAnswer()).doesNotContain("police");
    }

    @Test void supportedAnswerReturnsOnlyValidatedCitations() {
        when(embeddings.generateEmbedding(anyString())).thenReturn(List.of(1f));
        retrievedSource();
        when(generation.generateJson(anyString(), anyMap())).thenReturn("{\"status\":\"ANSWERED\",\"answer\":\"Consulter le superviseur.\",\"citations\":[{\"chunkId\":\"source-1\",\"quote\":\"consulter le superviseur\"}]}");
        RagResponse response = service.query(new RagQueryRequest(null, "Point inaccessible ?"), "driver");
        assertThat(response.getStatus()).isEqualTo("ANSWERED");
        assertThat(response.getSources()).hasSize(1);
        assertThat(response.getSources().get(0).getSnippet()).isEqualTo("consulter le superviseur");
    }

    @Test void independentShortQuestionDoesNotRetrievePreviousPhotoTopicDuringOutage() {
        String previous = "La photo APRÈS est-elle nécessaire ?";
        String salary = "Quel est le salaire annuel ?";
        when(histories.findById("existing")).thenReturn(Optional.of(RagConversation.builder().id("existing").userId("driver")
                .messages(new ArrayList<>(List.of(RagMessage.builder().sender("USER").content(previous).build()))).build()));
        when(embeddings.generateEmbedding(anyString())).thenThrow(new AiServiceException("Unavailable"));
        when(store.searchSimilar(eq(salary), any(), anyInt(), anyDouble())).thenReturn(List.of());
        RagResponse response = service.query(new RagQueryRequest("existing", salary), "driver");
        verify(embeddings).generateEmbedding(salary);
        verify(store).searchSimilar(eq(salary), isNull(), anyInt(), anyDouble());
        assertThat(response.getStatus()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(response.getSources()).isEmpty();
        assertThat(response.getAnswer()).doesNotContain("photo");
        verifyNoInteractions(generation);
    }

    @Test void explicitAnaphoricFollowUpRetainsPreviousUserTopic() {
        String previous = "La photo APRÈS est-elle nécessaire ?";
        String followUp = "Et si ce point est inaccessible ?";
        when(histories.findById("existing")).thenReturn(Optional.of(RagConversation.builder().id("existing").userId("driver")
                .messages(new ArrayList<>(List.of(RagMessage.builder().sender("USER").content(previous).build()))).build()));
        when(embeddings.generateEmbedding(anyString())).thenThrow(new AiServiceException("Unavailable"));
        retrievedSource();
        RagResponse response = service.query(new RagQueryRequest("existing", followUp), "driver");
        verify(embeddings).generateEmbedding(previous + "\n" + followUp);
        verify(store).searchSimilar(eq(previous + "\n" + followUp), isNull(), anyInt(), anyDouble());
        assertThat(response.getStatus()).isEqualTo("SOURCE_EXCERPTS");
        assertThat(response.getSources().get(0).getSnippet()).isEqualTo(chunk.getContent());
        verify(generation).generateJson(anyString(), anyMap());
    }

    @Test void generationOutageDoesNotPresentGenericDriverMatchesAsSalaryEvidence() {
        chunk.setContent("Le chauffeur prend une photo APRÈS la collecte normale et retourne au dépôt.");
        when(embeddings.generateEmbedding(anyString())).thenReturn(List.of(1f));
        retrievedSource();
        when(generation.generateJson(anyString(), anyMap())).thenThrow(new AiServiceException("Provider unavailable"));
        RagResponse response = service.query(new RagQueryRequest(null,
                "Quel est le salaire annuel du chauffeur et le montant de sa prime de nuit ?"), "driver");
        assertThat(response.getStatus()).isEqualTo("INSUFFICIENT_INFORMATION");
        assertThat(response.getSources()).isEmpty();
        assertThat(response.isRelevantContextFound()).isFalse();
        assertThat(response.getAnswer()).isEqualTo(RagService.NO_RELEVANT_CONTEXT_MESSAGE);
    }

    @Test void lexicalOutageFallbackKeepsRelevantExcerptsAndRemovesUnrelatedHits() {
        DocumentChunk unrelated = DocumentChunk.builder().id("other").documentId("other-doc")
                .content("Le point de collecte est illustré par une photo APRÈS.").build();
        when(embeddings.generateEmbedding(anyString())).thenThrow(new AiServiceException("Unavailable"));
        when(store.searchSimilar(anyString(), any(), anyInt(), anyDouble())).thenReturn(List.of(
                new VectorStoreService.ScoredChunk(chunk, .8), new VectorStoreService.ScoredChunk(unrelated, .7)));
        RagResponse response = service.query(new RagQueryRequest(null, "Point inaccessible ?"), "driver");
        assertThat(response.getStatus()).isEqualTo("SOURCE_EXCERPTS");
        assertThat(response.getSources()).extracting(SourceDto::getChunkId).containsExactly("source-1");
        assertThat(response.getAnswer()).doesNotContain(unrelated.getContent());
        verify(generation).generateJson(anyString(), anyMap());
    }

    @Test void shortFollowUpCanUseThePreviousQuestionForLiteralExcerpts() {
        String previous = "Point inaccessible ?";
        when(histories.findById("existing")).thenReturn(Optional.of(RagConversation.builder().id("existing").userId("driver")
                .messages(new ArrayList<>(List.of(RagMessage.builder().sender("USER").content(previous).build()))).build()));
        when(embeddings.generateEmbedding(anyString())).thenThrow(new AiServiceException("Unavailable"));
        retrievedSource();
        RagResponse response = service.query(new RagQueryRequest("existing", "Et ensuite ?"), "driver");
        assertThat(response.getStatus()).isEqualTo("SOURCE_EXCERPTS");
        assertThat(response.getSources()).extracting(SourceDto::getChunkId).containsExactly("source-1");
        verify(generation).generateJson(anyString(), anyMap());
    }

    @Test void greetingIsGeneratedWithoutSearchingDocumentsAndKindIsSaved() {
        when(generation.generateJson(anyString(), anyMap())).thenReturn("{\"status\":\"ANSWERED\",\"answer\":\"Hello! How can I help with your collection today?\",\"citations\":[]}");
        RagResponse response = service.query(new RagQueryRequest(null, "hello!"), "driver");
        assertThat(response.getStatus()).isEqualTo("ANSWERED");
        assertThat(response.getResponseKind()).isEqualTo("CONVERSATION");
        assertThat(response.getAnswer()).startsWith("Hello!");
        assertThat(response.getSources()).isEmpty();
        assertThat(response.getRetrievalMode()).isEqualTo("NOT_REQUIRED");
        verifyNoInteractions(embeddings, store);
        var saved = org.mockito.ArgumentCaptor.forClass(RagConversation.class);
        verify(histories).save(saved.capture());
        assertThat(saved.getValue().getMessages().get(1).getResponseKind()).isEqualTo("CONVERSATION");
    }

    @Test void greetingWithAProcedureCannotBypassGrounding() {
        retrievedSource();
        when(generation.generateJson(anyString(), anyMap())).thenReturn("{\"status\":\"ANSWERED\",\"answer\":\"Invented procedure\",\"citations\":[]}");
        var response = service.query(new RagQueryRequest(null, "Hello, que faire si le point est inaccessible ?"), "driver");
        assertThat(response.getResponseKind()).isEqualTo("GROUNDED");
        assertThat(response.getStatus()).isNotEqualTo("ANSWERED");
        assertThat(response.getAnswer()).doesNotContain("Invented");
        verify(embeddings).generateEmbedding(anyString());
    }

    @Test void greetingProviderFailureIsNotMisreportedAsMissingDocuments() {
        when(generation.generateJson(anyString(), anyMap())).thenThrow(new AiServiceException("503"));
        var response = service.query(new RagQueryRequest(null, "bonjour"), "driver");
        assertThat(response.getStatus()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(response.getAnswer()).doesNotContain("documents");
        verifyNoInteractions(embeddings, store);
    }

    @Test void explicitUserFactsCanBeRewrittenAsAnUnverifiedDraft() {
        when(generation.generateJson(anyString(), anyMap())).thenReturn("{\"status\":\"ANSWERED\",\"answer\":\"Bonjour, je signale que le point P2 est inaccessible.\",\"citations\":[]}");
        var response = service.query(new RagQueryRequest(null, "Rédige un message : le point P2 est inaccessible."), "driver");
        assertThat(response.getResponseKind()).isEqualTo("DRAFT");
        assertThat(response.getStatus()).isEqualTo("ANSWERED");
        assertThat(response.getSources()).isEmpty();
        verifyNoInteractions(embeddings, store);
        verify(generation).generateJson(contains("N'invente ni identité, horaire, lieu, cause, résultat"), anyMap());
    }

    @Test void requestToWriteOfficialInstructionsStillRequiresApprovedEvidence() {
        when(store.searchSimilar(anyString(), any(), anyInt(), anyDouble())).thenReturn(List.of());
        var response = service.query(new RagQueryRequest(null, "Rédige un message avec la procédure officielle pour les déchets chimiques."), "driver");
        assertThat(response.getStatus()).isEqualTo("INSUFFICIENT_INFORMATION");
        assertThat(response.getResponseKind()).isEqualTo("GROUNDED");
        verify(embeddings).generateEmbedding(anyString());
        verifyNoInteractions(generation);
    }

    @Test void healthyGenerationUsesApprovedBm25EvenWhenEmbeddingsFail() {
        when(embeddings.generateEmbedding(anyString())).thenThrow(new AiServiceException("503"));
        when(store.searchSimilar(anyString(), any(), anyInt(), anyDouble())).thenReturn(List.of(new VectorStoreService.ScoredChunk(chunk, .8, "LEXICAL")));
        when(generation.generateJson(anyString(), anyMap())).thenReturn("{\"status\":\"ANSWERED\",\"answer\":\"Consultez le superviseur.\",\"citations\":[{\"chunkId\":\"source-1\",\"quote\":\"consulter le superviseur\"}]}");
        var response = service.query(new RagQueryRequest(null, "Point inaccessible ?"), "driver");
        assertThat(response.getStatus()).isEqualTo("ANSWERED");
        assertThat(response.getRetrievalMode()).isEqualTo("LEXICAL");
        assertThat(response.getSources()).hasSize(1);
    }

    @Test void multipleFollowUpsAndThanksRetainSubstantiveTopic() {
        var history = RagConversation.builder().messages(List.of(
                RagMessage.builder().sender("USER").content("Quelle photo est obligatoire ?").build(),
                RagMessage.builder().sender("USER").content("Et si ce point est inaccessible ?").build(),
                RagMessage.builder().sender("USER").content("Merci !").build())).build();
        assertThat(service.retrievalQuery("Explique plus simplement", history))
                .isEqualTo("Quelle photo est obligatoire ?\nEt si ce point est inaccessible ?\nExplique plus simplement");
    }

    @Test void draftSuggestionAndFollowUpReuseUserFactsWithoutTurningAssistantTextIntoEvidence() {
        String original = "Aide-moi à rédiger un message : le point P2 est inaccessible.";
        when(histories.findById("draft")).thenReturn(Optional.of(RagConversation.builder().id("draft").userId("driver")
                .messages(new ArrayList<>(List.of(RagMessage.builder().sender("USER").content(original).build(),
                        RagMessage.builder().sender("ASSISTANT").content("UNSUPPORTED_PREVIOUS_TEXT").build()))).build()));
        when(generation.generateJson(anyString(), anyMap())).thenReturn("{\"status\":\"ANSWERED\",\"answer\":\"Point P2 inaccessible.\",\"citations\":[]}");
        var response = service.query(new RagQueryRequest("draft", "Plus court"), "driver");
        assertThat(response.getResponseKind()).isEqualTo("DRAFT");
        verify(generation).generateJson(argThat(prompt -> prompt.contains(original) && !prompt.contains("UNSUPPORTED_PREVIOUS_TEXT")), anyMap());
        verifyNoInteractions(embeddings, store);
    }

    @Test void mixedArabicAndEnglishGreetingStillRequiresGroundedRetrieval() {
        when(store.searchSimilar(anyString(), any(), anyInt(), anyDouble())).thenReturn(List.of());
        var response = service.query(new RagQueryRequest(null, "Hello ما هي إجراءات جمع النفايات؟"), "driver");
        assertThat(response.getResponseKind()).isEqualTo("GROUNDED");
        verify(embeddings).generateEmbedding(anyString());
        verifyNoInteractions(generation);
    }

    @Test void draftClarificationAcceptsFactsButDoesNotSwallowANewProceduralQuestion() {
        when(histories.findById("pending")).thenReturn(Optional.of(RagConversation.builder().id("pending").userId("driver")
                .messages(new ArrayList<>(List.of(RagMessage.builder().sender("ASSISTANT").responseKind("DRAFT")
                        .awaitingDraftDetails(true).draftUserFacts("Rédige un message au superviseur.").build()))).build()));
        when(generation.generateJson(anyString(), anyMap())).thenReturn("{\"status\":\"ANSWERED\",\"answer\":\"Le point P2 est inaccessible depuis 10 h.\",\"citations\":[],\"needsDetails\":false}");
        var response = service.query(new RagQueryRequest("pending", "Le point P2 est inaccessible depuis 10 h."), "driver");
        assertThat(response.getResponseKind()).isEqualTo("DRAFT");
        verifyNoInteractions(embeddings, store);
        when(store.searchSimilar(anyString(), any(), anyInt(), anyDouble())).thenReturn(List.of());
        assertThat(service.query(new RagQueryRequest("pending", "Le point exige quelle procédure officielle ?"), "driver").getResponseKind())
                .isEqualTo("GROUNDED");
    }

    @Test void styleFollowUpsRetainUsefulLiteralPassagesDuringOutage() {
        when(histories.findById("existing")).thenReturn(Optional.of(RagConversation.builder().id("existing").userId("driver")
                .messages(new ArrayList<>(List.of(RagMessage.builder().sender("USER").content("Point inaccessible ?").build(),
                        RagMessage.builder().sender("USER").content("Plus simple").build(),
                        RagMessage.builder().sender("USER").content("Plus court").build(),
                        RagMessage.builder().sender("USER").content("Résumé simplement").build()))).build()));
        when(embeddings.generateEmbedding(anyString())).thenThrow(new AiServiceException("503"));
        when(generation.generateJson(anyString(), anyMap())).thenThrow(new AiServiceException("503"));
        retrievedSource();
        var response = service.query(new RagQueryRequest("existing", "Explique plus simplement"), "driver");
        verify(embeddings).generateEmbedding("Point inaccessible ?\nExplique plus simplement");
        assertThat(response.getStatus()).isEqualTo("SOURCE_EXCERPTS");
        assertThat(response.getSources()).extracting(SourceDto::getChunkId).containsExactly("source-1");
    }
}
