package com.municipalite.gestiontournees.ai.rag.service;

import com.municipalite.gestiontournees.ai.config.AiProperties;
import com.municipalite.gestiontournees.ai.rag.model.AiDocument;
import com.municipalite.gestiontournees.ai.rag.model.DocumentChunk;
import com.municipalite.gestiontournees.ai.rag.service.DocumentLoaderService.ExtractedPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service de découpage (chunking) configurable des textes documentaires avec chevauchement (overlap) et extraction de titre de section.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChunkingService {

    private final AiProperties aiProperties;
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?m)^(?:[0-9]+\\.|[A-Z\\s]{4,}:?|#{1,3}\\s*)(.+)");

    public List<DocumentChunk> createChunks(AiDocument doc, List<ExtractedPage> pages) {
        int chunkSize = aiProperties.getRag().getChunkSize();
        int chunkOverlap = aiProperties.getRag().getChunkOverlap();

        List<DocumentChunk> chunks = new ArrayList<>();
        int globalChunkIndex = 0;

        for (ExtractedPage page : pages) {
            String text = page.getText();
            if (text == null || text.trim().isEmpty()) {
                continue;
            }

            String currentSectionTitle = extractSectionTitle(text);

            List<String> textChunks = splitTextWithOverlap(text, chunkSize, chunkOverlap);
            for (String chunkText : textChunks) {
                if (chunkText.trim().length() < 20) {
                    continue; // Ignorer les tranches insignifiantes
                }

                DocumentChunk chunk = DocumentChunk.builder()
                        .documentId(doc.getId())
                        .documentName(doc.getOriginalName())
                        .fileType(doc.getFileType())
                        .pageNumber(page.getPageNumber())
                        .chunkIndex(globalChunkIndex++)
                        .sectionTitle(currentSectionTitle)
                        .content(chunkText.trim())
                        .createdAt(LocalDateTime.now())
                        .build();

                chunks.add(chunk);
            }
        }

        log.info("Document {} découpé en {} chunks (size={}, overlap={})", doc.getOriginalName(), chunks.size(), chunkSize, chunkOverlap);
        return chunks;
    }

    private String extractSectionTitle(String text) {
        Matcher matcher = TITLE_PATTERN.matcher(text);
        if (matcher.find()) {
            String title = matcher.group(1).trim();
            if (title.length() > 60) {
                return title.substring(0, 60) + "...";
            }
            return title;
        }
        return "Général";
    }

    private List<String> splitTextWithOverlap(String text, int chunkSize, int overlap) {
        List<String> result = new ArrayList<>();
        int start = 0;
        int length = text.length();

        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            
            // Essayer de couper sur une fin de phrase ou saut de ligne si possible
            if (end < length) {
                int lastBoundary = Math.max(text.lastIndexOf('.', end), text.lastIndexOf('\n', end));
                if (lastBoundary > start + (chunkSize / 2)) {
                    end = lastBoundary + 1;
                }
            }

            String slice = text.substring(start, end);
            result.add(slice);

            if (end >= length) {
                break;
            }

            // Reculer du montant d'overlap pour la tranche suivante
            start = Math.max(end - overlap, start + 1);
        }

        return result;
    }
}
