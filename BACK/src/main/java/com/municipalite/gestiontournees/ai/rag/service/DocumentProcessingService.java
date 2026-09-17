package com.municipalite.gestiontournees.ai.rag.service;

import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.rag.model.AiDocument;
import com.municipalite.gestiontournees.ai.rag.model.AiDocument.DocumentStatus;
import com.municipalite.gestiontournees.ai.rag.model.DocumentChunk;
import com.municipalite.gestiontournees.ai.rag.repository.AiDocumentRepository;
import com.municipalite.gestiontournees.ai.rag.service.DocumentLoaderService.ExtractedPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
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
@RequiredArgsConstructor
public class DocumentProcessingService {

    private final AiDocumentRepository aiDocumentRepository;
    private final DocumentLoaderService documentLoaderService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final AiProperties aiProperties;

    /**
     * Sauvegarde le fichier sur disque, crée la notice AiDocument et déclenche l'indexation asynchrone.
     */
    public AiDocument uploadDocument(MultipartFile file, String uploadedBy) throws IOException {
        String originalName = file.getOriginalFilename();
        String fileType = extractExtension(originalName);
        long fileSize = file.getSize();

        String storageDirStr = aiProperties.getStorage().getDocumentsDir();
        Path uploadDir = Paths.get(storageDirStr);
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        String savedFileName = UUID.randomUUID() + "_" + originalName;
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

        return doc;
    }

    /**
     * Pipeline d'indexation asynchrone : Extraction -> Chunking -> Embeddings -> Vector Store -> INDEXED.
     */
    @Async
    public void processDocumentAsync(String documentId) {
        AiDocument doc = aiDocumentRepository.findById(documentId).orElse(null);
        if (doc == null) return;

        try {
            doc.setStatus(DocumentStatus.PROCESSING);
            doc.setErrorMessage(null);
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

            // 3. Calcul des Embeddings
            for (DocumentChunk chunk : chunks) {
                List<Float> embedding = embeddingService.generateEmbedding(chunk.getContent());
                chunk.setEmbedding(embedding);
            }

            // 4. Stockage dans la base vectorielle
            vectorStoreService.saveChunks(chunks);

            // 5. Mise à jour statut INDEXED
            doc.setStatus(DocumentStatus.INDEXED);
            doc.setChunkCount(chunks.size());
            doc.setIndexedAt(LocalDateTime.now());
            aiDocumentRepository.save(doc);

            log.info("Indexation RAG réussie pour le document: {} ({} chunks)", doc.getOriginalName(), chunks.size());

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
