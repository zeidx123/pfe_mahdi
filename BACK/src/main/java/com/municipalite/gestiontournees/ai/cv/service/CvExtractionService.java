package com.municipalite.gestiontournees.ai.cv.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CvExtractionService {
    public record TextPart(Integer pageNumber, String location, String text) {}
    public record Extraction(List<TextPart> parts, String status) {
        public String text() { return parts.stream().map(p -> "[" + p.location() + "]\n" + p.text()).reduce("", (a, b) -> a + "\n" + b); }
    }

    public Extraction extract(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Le CV doit contenir un fichier de 10 Mo maximum");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".pdf") && !name.endsWith(".docx")) throw new IllegalArgumentException("Format accepté : PDF ou DOCX");
        var parts = new ArrayList<TextPart>();
        try {
            if (name.endsWith(".pdf")) {
                try (var document = Loader.loadPDF(file.getBytes())) {
                    var stripper = new PDFTextStripper(); stripper.setSortByPosition(true);
                    for (int page = 1; page <= document.getNumberOfPages(); page++) {
                        stripper.setStartPage(page); stripper.setEndPage(page);
                        String text = stripper.getText(document).trim();
                        if (!text.isBlank()) parts.add(new TextPart(page, "Page " + page, text));
                    }
                }
            } else {
                try (var document = new XWPFDocument(file.getInputStream())) {
                    int index = 0;
                    for (var paragraph : document.getParagraphs()) {
                        index++;
                        if (!paragraph.getText().isBlank()) parts.add(new TextPart(null, "Paragraphe " + index, paragraph.getText()));
                    }
                    int tableIndex = 0;
                    for (var table : document.getTables()) {
                        tableIndex++;
                        for (int row = 0; row < table.getRows().size(); row++) {
                            String text = table.getRow(row).getTableCells().stream().map(c -> c.getText()).reduce("", (a, b) -> a + " | " + b);
                            if (!text.isBlank()) parts.add(new TextPart(null, "Tableau " + tableIndex + ", ligne " + (row + 1), text));
                        }
                    }
                }
            }
        } catch (Exception error) {
            return new Extraction(List.of(), "MANUAL_REVIEW_REQUIRED");
        }
        return new Extraction(parts, parts.isEmpty() ? "MANUAL_REVIEW_REQUIRED" : "EXTRACTED");
    }

    /** Compatibility entry point for callers that only need text. */
    public String extractTextFromPdf(MultipartFile file) { return extract(file).text(); }
}
