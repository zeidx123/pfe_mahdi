package com.municipalite.gestiontournees.ai.rag.service;

import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.rag.model.AiDocument;
import com.municipalite.gestiontournees.ai.rag.model.AiDocument.DocumentStatus;
import com.municipalite.gestiontournees.ai.rag.model.DocumentChunk;
import com.municipalite.gestiontournees.ai.rag.repository.AiDocumentRepository;
import com.municipalite.gestiontournees.ai.rag.service.DocumentLoaderService.ExtractedPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service gérant le cycle de vie, le stockage physique et le traitement d'indexation asynchrone des documents.
 */
@Service
@Slf4j
public class DocumentProcessingService {

    private final AiDocumentRepository aiDocumentRepository;
    private final DocumentLoaderService documentLoaderService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final AiProperties aiProperties;
    private final TaskExecutor taskExecutor;

    public DocumentProcessingService(AiDocumentRepository aiDocumentRepository, DocumentLoaderService documentLoaderService,
            ChunkingService chunkingService, EmbeddingService embeddingService, VectorStoreService vectorStoreService,
            AiProperties aiProperties, @org.springframework.beans.factory.annotation.Qualifier("documentTaskExecutor") TaskExecutor taskExecutor) {
        this.aiDocumentRepository = aiDocumentRepository;
        this.documentLoaderService = documentLoaderService;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.aiProperties = aiProperties;
        this.taskExecutor = taskExecutor;
    }


    /** Interrupted in-memory tasks are made manually retryable; no provider call is replayed. */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void recoverInterruptedDocuments() {
        for (AiDocument doc : aiDocumentRepository.findAll()) {
            if (doc.getStatus() != DocumentStatus.PROCESSING) continue;
            int count = vectorStoreService.usableChunkCount(doc.getId());
            doc.setStatus(count > 0 ? DocumentStatus.TEXT_READY : DocumentStatus.UPLOADED);
            doc.setChunkCount(count);
            doc.setEmbeddingModel(null);
            doc.setEmbeddingVersion(null);
            doc.setEmbeddingDimensions(null);
            doc.setIndexedAt(null);
            doc.setErrorMessage("Traitement interrompu par un redémarrage. "
                    + (count > 0 ? "Les extraits enregistrés restent disponibles. " : "Le texte doit être extrait. ")
                    + "Relancez manuellement l'indexation.");
            aiDocumentRepository.save(doc);
        }
    }

    /**
     * Sauvegarde le fichier sur disque, crée la notice AiDocument et déclenche l'indexation asynchrone.
     */
    public AiDocument uploadDocument(MultipartFile file, String uploadedBy) throws IOException {
        String originalName = file.getOriginalFilename();
        String fileType = extractExtension(originalName);
        long fileSize = file.getSize();

        String storageDirStr = aiProperties.getStorage().getDocumentsDir();
        Path uploadDir = Paths.get(storageDirStr).toAbsolutePath().normalize();
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        if (!java.util.Set.of("PDF", "DOCX", "TXT", "MD").contains(fileType) || file.isEmpty()) {
            throw new IllegalArgumentException("Fichier PDF, DOCX ou texte non vide requis.");
        }
        String savedFileName = UUID.randomUUID() + "." + fileType.toLowerCase();
        Path targetPath = uploadDir.resolve(savedFileName);
        Files.copy(file.getInputStream(), targetPath);

        AiDocument doc = AiDocument.builder()
                .originalName(originalName)
                .fileType(fileType)
                .fileSize(fileSize)
                .filePath(targetPath.toString())
                .status(DocumentStatus.UPLOADED)
                .chunkCount(0)
                .uploadedBy(uploadedBy)
                .createdAt(LocalDateTime.now())
                .build();

        doc = aiDocumentRepository.save(doc);

        // Déclencher le traitement d'indexation
        processDocumentAsync(doc.getId());
        doc.setStatus(DocumentStatus.PROCESSING);
        return doc;
    }

    /**
     * Pipeline d'indexation asynchrone : Extraction -> Chunking -> Embeddings -> Vector Store -> INDEXED.
     */
    public void processDocumentAsync(String documentId) {
        AiDocument doc = aiDocumentRepository.findById(documentId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Document introuvable."));
        if (doc.getStatus() == DocumentStatus.PROCESSING) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Le document est déjà en cours de traitement.");
        DocumentStatus previousStatus = doc.getStatus();
        doc.setStatus(DocumentStatus.PROCESSING);
        aiDocumentRepository.save(doc);
        try {
            taskExecutor.execute(() -> processDocument(documentId));
        } catch (org.springframework.core.task.TaskRejectedException e) {
            doc.setStatus(previousStatus);
            doc.setErrorMessage("Le traitement documentaire est occupé. Réessayez l'indexation dans quelques instants.");
            aiDocumentRepository.save(doc);
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, doc.getErrorMessage());
        }
    }

    void processDocument(String documentId) {
        AiDocument doc = aiDocumentRepository.findById(documentId).orElse(null);
        if (doc == null) return;

        try {
            doc.setStatus(DocumentStatus.PROCESSING);
            doc.setErrorMessage(null);
            doc.setEmbeddingModel(null);
            doc.setEmbeddingVersion(null);
            doc.setEmbeddingDimensions(null);
            aiDocumentRepository.save(doc);

            // Nettoyer les anciens chunks si réindexation
            vectorStoreService.deleteByDocumentId(doc.getId());

            File file = new File(doc.getFilePath());
            if (!file.exists()) {
                throw new IOException("Fichier introuvable sur le disque: " + doc.getFilePath());
            }

            // 1. Extraction du texte
            List<ExtractedPage> pages = documentLoaderService.extractTextPages(file, doc.getFileType());
            if (pages.isEmpty()) {
                throw new IllegalArgumentException("Aucun texte réutilisable n'a pu être extrait du document.");
            }

            // 2. Chunking
            List<DocumentChunk> chunks = chunkingService.createChunks(doc, pages);
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("Le document n'a produit aucun chunk de texte exploitable.");
            }

            // Text survives provider failure, allowing honest lexical source excerpts.
            vectorStoreService.saveChunks(chunks);
            doc.setChunkCount(chunks.size());
            // Keep persisted PROCESSING until provider work settles; approval is blocked during processing.
            doc.setStatus(DocumentStatus.TEXT_READY);
            try {
                for (DocumentChunk chunk : chunks) {
                    List<Float> embedding = embeddingService.generateEmbedding(chunk.getContent());
                    chunk.setEmbedding(embedding);
                    chunk.setEmbeddingModel(aiProperties.getRag().getEmbeddingModel());
                    chunk.setEmbeddingDimensions(embedding.size());
                    chunk.setEmbeddingVersion(aiProperties.getRag().getEmbeddingVersion());
                }
                vectorStoreService.saveChunks(chunks);
                doc.setStatus(DocumentStatus.INDEXED);
                doc.setEmbeddingModel(aiProperties.getRag().getEmbeddingModel());
                doc.setEmbeddingVersion(aiProperties.getRag().getEmbeddingVersion());
                doc.setEmbeddingDimensions(aiProperties.getRag().getEmbeddingDimensions());
                doc.setIndexedAt(LocalDateTime.now());
            } catch (com.municipalite.gestiontournees.ai.common.AiServiceException e) {
                doc.setErrorMessage("Texte disponible ; index sémantique indisponible. Réessayez l'indexation.");
            }
            aiDocumentRepository.save(doc);

            log.info("Traitement RAG : document={} état={} chunks={}", doc.getId(), doc.getStatus(), chunks.size());

        } catch (Exception e) {
            log.error("Échec de l'indexation RAG du document {}: {}", doc.getOriginalName(), e.getMessage(), e);
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage(e.getMessage());
            aiDocumentRepository.save(doc);
        }
    }

    /**
     * Supprime un document de la base de données et du disque ainsi que ses chunks vectoriels.
     */
    public void deleteDocument(String documentId) {
        AiDocument doc = aiDocumentRepository.findById(documentId).orElse(null);
        if (doc != null) {
            if (doc.getStatus() == DocumentStatus.PROCESSING) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Attendez la fin du traitement avant de supprimer ce document.");
            vectorStoreService.deleteByDocumentId(documentId);
            try {
                Path path = Paths.get(doc.getFilePath());
                Files.deleteIfExists(path);
            } catch (Exception e) {
                log.warn("Impossible de supprimer le fichier physique: {}", e.getMessage());
            }
            aiDocumentRepository.deleteById(documentId);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "TXT";
        return filename.substring(filename.lastIndexOf('.') + 1).toUpperCase();
    }
}
