package com.municipalite.gestiontournees.ai.rag.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Service chargé du chargement et de l'extraction de texte brut par page/section depuis divers formats (PDF, DOCX, TXT).
 */
@Service
@Slf4j
public class DocumentLoaderService {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedPage {
        private int pageNumber;
        private String text;
    }

    /**
     * Extrait le texte d'un fichier en découpant par page.
     */
    public List<ExtractedPage> extractTextPages(File file, String fileType) throws IOException {
        String type = fileType != null ? fileType.toUpperCase() : "";

        if (type.contains("PDF")) {
            return extractFromPdf(file);
        } else if (type.contains("DOCX") || type.contains("WORD")) {
            return extractFromDocx(file);
        } else {
            // TXT ou Markdown
            return extractFromTxt(file);
        }
    }

    private List<ExtractedPage> extractFromPdf(File file) throws IOException {
        List<ExtractedPage> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(file)) {
            int totalPages = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();

            for (int i = 1; i <= totalPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(document).trim();
                if (!text.isEmpty()) {
                    pages.add(new ExtractedPage(i, text));
                }
            }
        }
        return pages;
    }

    private List<ExtractedPage> extractFromDocx(File file) throws IOException {
        List<ExtractedPage> pages = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {
            
            StringBuilder sb = new StringBuilder();
            int pageEstimate = 1;

            for (XWPFParagraph p : document.getParagraphs()) {
                String text = p.getText().trim();
                if (!text.isEmpty()) {
                    sb.append(text).append("\n");
                    // Simulation de fin de page toutes les ~2500 caractres
                    if (sb.length() > 2500) {
                        pages.add(new ExtractedPage(pageEstimate++, sb.toString().trim()));
                        sb.setLength(0);
                    }
                }
            }
            if (sb.length() > 0) {
                pages.add(new ExtractedPage(pageEstimate, sb.toString().trim()));
            }
        }
        return pages;
    }

    private List<ExtractedPage> extractFromTxt(File file) throws IOException {
        List<ExtractedPage> pages = new ArrayList<>();
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        
        // Découpage virtuel par paquet de 2000 caractères
        int pageSize = 2000;
        int len = content.length();
        int pageNumber = 1;

        for (int i = 0; i < len; i += pageSize) {
            String pageText = content.substring(i, Math.min(len, i + pageSize)).trim();
            if (!pageText.isEmpty()) {
                pages.add(new ExtractedPage(pageNumber++, pageText));
            }
        }

        if (pages.isEmpty() && !content.trim().isEmpty()) {
            pages.add(new ExtractedPage(1, content.trim()));
        }

        return pages;
    }
}
