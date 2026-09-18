package com.municipalite.gestiontournees.ai.rag.controller;

import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.rag.dto.DocumentInfoDto;
import com.municipalite.gestiontournees.ai.rag.model.AiDocument;
import com.municipalite.gestiontournees.ai.rag.repository.AiDocumentRepository;
import com.municipalite.gestiontournees.ai.rag.service.DocumentProcessingService;
import com.municipalite.gestiontournees.security.AccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.PathResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/ai/documents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERVISEUR')")
public class DocumentController {
    private final DocumentProcessingService documentProcessingService;
    private final AiDocumentRepository aiDocumentRepository;
    private final AiProperties aiProperties;
    private final AccessService accessService;

    @PostMapping
    public DocumentInfoDto uploadDocument(@RequestParam("file") MultipartFile file) throws IOException {
        return toDto(documentProcessingService.uploadDocument(file, accessService.currentUser().getEmail()));
    }

    @GetMapping
    public List<DocumentInfoDto> getAllDocuments() { return aiDocumentRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toDto).toList(); }

    @GetMapping("/{id}")
    public DocumentInfoDto getDocumentById(@PathVariable String id) { return toDto(find(id)); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String id) {
        documentProcessingService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reindex")
    public DocumentInfoDto reindexDocument(@PathVariable String id) {
        AiDocument doc = find(id);
        documentProcessingService.processDocumentAsync(id);
        doc.setStatus(AiDocument.DocumentStatus.PROCESSING);
        return toDto(doc);
    }

    public record Approval(boolean approved) {}
    @PatchMapping("/{id}/approval")
    public DocumentInfoDto approve(@PathVariable String id, @RequestBody Approval request) {
        AiDocument doc = find(id);
        if (doc.getStatus() == AiDocument.DocumentStatus.PROCESSING || doc.getStatus() == AiDocument.DocumentStatus.UPLOADED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attendez la fin du traitement avant de modifier l’approbation.");
        }
        doc.setApproved(request.approved());
        doc.setApprovedBy(accessService.currentUser().getId());
        doc.setApprovedAt(LocalDateTime.now());
        return toDto(aiDocumentRepository.save(doc));
    }

    @GetMapping("/{id}/source")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> source(@PathVariable String id) throws IOException {
        AiDocument doc = find(id);
        if (!accessService.isSupervisor() && !doc.isApproved()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Document non approuvé.");
        }
        Path root = Paths.get(aiProperties.getStorage().getDocumentsDir()).toAbsolutePath().normalize();
        Path path = Paths.get(doc.getFilePath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !path.toRealPath().startsWith(root.toRealPath())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Source indisponible.");
        }
        MediaType type = "PDF".equals(doc.getFileType()) ? MediaType.APPLICATION_PDF : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(type).header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(doc.getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .body(new PathResource(path));
    }

    private AiDocument find(String id) {
        return aiDocumentRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable."));
    }

    private DocumentInfoDto toDto(AiDocument doc) {
        boolean semanticReady = doc.getStatus() == AiDocument.DocumentStatus.INDEXED
                && Objects.equals(doc.getEmbeddingModel(), aiProperties.getRag().getEmbeddingModel())
                && Objects.equals(doc.getEmbeddingVersion(), aiProperties.getRag().getEmbeddingVersion())
                && Objects.equals(doc.getEmbeddingDimensions(), aiProperties.getRag().getEmbeddingDimensions());
        return DocumentInfoDto.builder().id(doc.getId()).originalName(doc.getOriginalName()).fileType(doc.getFileType()).fileSize(doc.getFileSize())
                .status(doc.getStatus()).chunkCount(doc.getChunkCount()).errorMessage(doc.getErrorMessage())
                .approved(doc.isApproved()).approvedBy(doc.getApprovedBy()).approvedAt(doc.getApprovedAt())
                .textReady(doc.getStatus() == AiDocument.DocumentStatus.INDEXED || doc.getStatus() == AiDocument.DocumentStatus.TEXT_READY)
                .semanticIndexReady(semanticReady).embeddingModel(doc.getEmbeddingModel()).embeddingVersion(doc.getEmbeddingVersion())
                .embeddingDimensions(doc.getEmbeddingDimensions()).uploadedBy(doc.getUploadedBy()).createdAt(doc.getCreatedAt()).indexedAt(doc.getIndexedAt()).build();
    }
}
