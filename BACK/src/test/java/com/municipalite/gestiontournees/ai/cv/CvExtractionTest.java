package com.municipalite.gestiontournees.ai.cv;

import com.municipalite.gestiontournees.ai.cv.service.CvExtractionService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class CvExtractionTest {
    private final CvExtractionService service = new CvExtractionService();

    @Test void pdfKeepsActualPageReferencesAndBlankScanRequiresReview() throws Exception {
        try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            var second = new PDPage(); document.addPage(second);
            try (var content = new PDPageContentStream(document, second)) {
                content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(40, 700); content.showText("Permis : CE"); content.endText();
            }
            document.save(bytes);
            var result = service.extract(new MockMultipartFile("cv", "staged.pdf", "application/pdf", bytes.toByteArray()));
            assertEquals("EXTRACTED", result.status());
            assertEquals(2, result.parts().get(0).pageNumber());
        }
        try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            document.addPage(new PDPage()); document.save(bytes);
            var result = service.extract(new MockMultipartFile("cv", "blank.pdf", "application/pdf", bytes.toByteArray()));
            assertEquals("MANUAL_REVIEW_REQUIRED", result.status()); assertTrue(result.parts().isEmpty());
        }
    }

    @Test void docxIncludesTableEvidenceAndParagraphLocations() throws Exception {
        try (var document = new XWPFDocument(); var bytes = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Nom : Exemple");
            document.createTable(1, 1).getRow(0).getCell(0).setText("Permis : C");
            document.write(bytes);
            var result = service.extract(new MockMultipartFile("cv", "staged.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes.toByteArray()));
            assertEquals(2, result.parts().size());
            assertEquals("Paragraphe 1", result.parts().get(0).location());
            assertEquals("Tableau 1, ligne 1", result.parts().get(1).location());
            assertNull(result.parts().get(1).pageNumber());
        }
    }
}
