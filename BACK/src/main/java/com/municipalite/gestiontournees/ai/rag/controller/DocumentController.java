package com.municipalite.gestiontournees.ai.rag.controller;

import com.municipalite.gestiontournees.ai.rag.dto.DocumentInfoDto;
import com.municipalite.gestiontournees.ai.rag.model.AiDocument;
import com.municipalite.gestiontournees.ai.rag.repository.AiDocumentRepository;
import com.municipalite.gestiontournees.ai.rag.service.DocumentProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Contrôleur REST d'administration de la base documentaire RAG.
 * Accessible aux administrateurs et superviseurs.
 */
@RestController
@RequestMapping("/api/ai/documents")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPERVISEUR', 'ADMIN')")
public class DocumentController {

    private final DocumentProcessingService documentProcessingService;
    private final AiDocumentRepository aiDocumentRepository;

    @PostMapping
    public ResponseEntity<DocumentInfoDto> uploadDocument(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {
        String username = authentication != null ? authentication.getName() : "admin@municipalite.ma";
        AiDocument doc = documentProcessingService.uploadDocument(file, username);
        return ResponseEntity.ok(toDto(doc));
    }

    @GetMapping
    public ResponseEntity<List<DocumentInfoDto>> getAllDocuments() {
        List<AiDocument> docs = aiDocumentRepository.findAllByOrderByCreatedAtDesc();
        List<DocumentInfoDto> dtos = docs.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentInfoDto> getDocumentById(@PathVariable String id) {
        return aiDocumentRepository.findById(id)
                .map(doc -> ResponseEntity.ok(toDto(doc)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String id) {
        documentProcessingService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reindex")
    public ResponseEntity<DocumentInfoDto> reindexDocument(@PathVariable String id) {
        return aiDocumentRepository.findById(id).map(doc -> {
            documentProcessingService.processDocumentAsync(id);
            return ResponseEntity.ok(toDto(doc));
        }).orElse(ResponseEntity.notFound().build());
    }

    private DocumentInfoDto toDto(AiDocument doc) {
        return DocumentInfoDto.builder()
                .id(doc.getId())
                .originalName(doc.getOriginalName())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .status(doc.getStatus())
                .chunkCount(doc.getChunkCount())
                .errorMessage(doc.getErrorMessage())
                .uploadedBy(doc.getUploadedBy())
                .createdAt(doc.getCreatedAt())
                .indexedAt(doc.getIndexedAt())
                .build();
    }
}
