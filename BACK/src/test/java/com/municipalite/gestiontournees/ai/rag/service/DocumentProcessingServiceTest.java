package com.municipalite.gestiontournees.ai.rag.service;

import com.municipalite.gestiontournees.ai.common.AiServiceException;
import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.rag.model.*;
import com.municipalite.gestiontournees.ai.rag.repository.AiDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.task.SyncTaskExecutor;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentProcessingServiceTest {
    @TempDir Path temp;
    @Test void usableTextIsSavedEvenWhenEmbeddingProviderFails() throws Exception {
        Path file = temp.resolve("procedure.txt"); Files.writeString(file, "Une procédure test valide pour les agents de collecte.");
        AiDocumentRepository documents = mock(AiDocumentRepository.class);
        EmbeddingService embeddings = mock(EmbeddingService.class);
        VectorStoreService store = mock(VectorStoreService.class);
        AiDocument doc = AiDocument.builder().id("doc").fileType("TXT").originalName("procedure.txt").filePath(file.toString()).build();
        when(documents.findById("doc")).thenReturn(Optional.of(doc));
        var persistedStatus = new java.util.concurrent.atomic.AtomicReference<AiDocument.DocumentStatus>();
        when(documents.save(any())).thenAnswer(call -> {
            AiDocument saved = call.getArgument(0);
            persistedStatus.set(saved.getStatus());
            return saved;
        });
        when(embeddings.generateEmbedding(anyString())).thenAnswer(call -> {
            assertThat(persistedStatus.get()).isEqualTo(AiDocument.DocumentStatus.PROCESSING);
            throw new AiServiceException("Unavailable");
        });
        AiProperties properties = new AiProperties();
        new DocumentProcessingService(documents, new DocumentLoaderService(), new ChunkingService(properties), embeddings, store, properties, new SyncTaskExecutor()).processDocumentAsync("doc");
        assertThat(doc.getStatus()).isEqualTo(AiDocument.DocumentStatus.TEXT_READY);
        assertThat(doc.getChunkCount()).isEqualTo(1);
        assertThat(doc.getEmbeddingModel()).isNull();
        verify(store).saveChunks(argThat(list -> list.size() == 1 && list.get(0).getEmbedding() == null));
    }

    @Test void rejectedSubmissionRestoresPreviousStatusAndCanBeRetried() {
        AiDocumentRepository documents = mock(AiDocumentRepository.class);
        var executor = mock(org.springframework.core.task.TaskExecutor.class);
        AiDocument doc = AiDocument.builder().id("doc").status(AiDocument.DocumentStatus.TEXT_READY).build();
        when(documents.findById("doc")).thenReturn(Optional.of(doc));
        doThrow(new org.springframework.core.task.TaskRejectedException("Full")).when(executor).execute(any());
        AiProperties properties = new AiProperties();
        var service = new DocumentProcessingService(documents, new DocumentLoaderService(), new ChunkingService(properties),
                mock(EmbeddingService.class), mock(VectorStoreService.class), properties, executor);
        assertThatThrownBy(() -> service.processDocumentAsync("doc"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("503");
        assertThat(doc.getStatus()).isEqualTo(AiDocument.DocumentStatus.TEXT_READY);
        assertThat(doc.getErrorMessage()).contains("Réessayez");
    }

    @Test void restartRestoresRetryableStatesFromActualChunksWithoutReplayingProviderWork() {
        AiDocumentRepository documents = mock(AiDocumentRepository.class);
        VectorStoreService store = mock(VectorStoreService.class);
        EmbeddingService embeddings = mock(EmbeddingService.class);
        var executor = mock(org.springframework.core.task.TaskExecutor.class);
        AiDocument withText = AiDocument.builder().id("with-text").status(AiDocument.DocumentStatus.PROCESSING)
                .approved(true).embeddingModel("old-model").embeddingVersion("old-version").embeddingDimensions(768).build();
        AiDocument withoutText = AiDocument.builder().id("without-text").status(AiDocument.DocumentStatus.PROCESSING).chunkCount(7).build();
        AiDocument completed = AiDocument.builder().id("completed").status(AiDocument.DocumentStatus.INDEXED).build();
        when(documents.findAll()).thenReturn(List.of(withText, withoutText, completed));
        when(store.usableChunkCount("with-text")).thenReturn(3);
        when(store.usableChunkCount("without-text")).thenReturn(0);
        AiProperties properties = new AiProperties();
        var service = new DocumentProcessingService(documents, new DocumentLoaderService(), new ChunkingService(properties),
                embeddings, store, properties, executor);
        service.recoverInterruptedDocuments();
        assertThat(withText.getStatus()).isEqualTo(AiDocument.DocumentStatus.TEXT_READY);
        assertThat(withText.getChunkCount()).isEqualTo(3);
        assertThat(withText.isApproved()).isTrue();
        assertThat(withText.getEmbeddingModel()).isNull();
        assertThat(withText.getErrorMessage()).contains("interrompu", "manuellement");
        assertThat(withoutText.getStatus()).isEqualTo(AiDocument.DocumentStatus.UPLOADED);
        assertThat(withoutText.getChunkCount()).isZero();
        assertThat(completed.getStatus()).isEqualTo(AiDocument.DocumentStatus.INDEXED);
        verify(documents, never()).save(completed);
        verifyNoInteractions(embeddings, executor);
        when(documents.findById("with-text")).thenReturn(Optional.of(withText));
        service.processDocumentAsync("with-text");
        verify(executor).execute(any());
        assertThat(withText.getStatus()).isEqualTo(AiDocument.DocumentStatus.PROCESSING);
    }
}
