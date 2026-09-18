package com.municipalite.gestiontournees.ai.rag.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;

class DocumentLoaderServiceTest {
    @TempDir Path temp;
    @Test void docxIncludesTablesAndTruthfulParagraphReferencesInsteadOfInventedPages() throws Exception {
        Path file = temp.resolve("staged.docx");
        try (var document = new XWPFDocument(); var output = Files.newOutputStream(file)) {
            document.createParagraph().createRun().setText("Consulter le superviseur.");
            document.createTable(1, 2).getRow(0).getCell(0).setText("Obstacle");
            document.getTables().get(0).getRow(0).getCell(1).setText("Accès indisponible");
            document.write(output);
        }
        var blocks = new DocumentLoaderService().extractTextPages(file.toFile(), "DOCX");
        assertThat(blocks).hasSize(2).allMatch(p -> p.getPageNumber() == 0);
        assertThat(blocks.get(0).getSectionTitle()).contains("paragraphe 1");
        assertThat(blocks.get(1).getSectionTitle()).contains("tableau 1, ligne 1");
        assertThat(blocks.get(1).getText()).contains("Obstacle", "Accès indisponible");
    }
}
