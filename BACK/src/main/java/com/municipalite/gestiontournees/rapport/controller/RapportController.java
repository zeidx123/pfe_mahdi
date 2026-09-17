package com.municipalite.gestiontournees.rapport.controller;

import com.municipalite.gestiontournees.rapport.service.PdfRapportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rapports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERVISEUR')")
public class RapportController {

    private final PdfRapportService pdfRapportService;

    @GetMapping("/tournee/{id}/pdf")
    public ResponseEntity<byte[]> getTourneeReportPdf(@PathVariable String id) {
        byte[] pdfBytes = pdfRapportService.generateTourneeReport(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Rapport_Tournee_" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
