package com.municipalite.gestiontournees.ai.cv.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;



/**
 * Service d'extraction du texte brut à partir d'un fichier CV (PDF).
 * Utilise Apache PDFBox — aucun appel externe, 100% local.
 */
@Service
@Slf4j
public class CvExtractionService {

    /**
     * Extrait le texte brut d'un fichier PDF uploadé.
     *
     * @param file Le fichier PDF du CV
     * @return Texte extrait du PDF
     * @throws RuntimeException Si le fichier est invalide ou illisible
     */
    public String extractTextFromPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Le fichier CV est vide ou invalide.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            throw new RuntimeException("Format de fichier non supporté. Veuillez uploader un fichier PDF.");
        }

        try {
            byte[] pdfBytes = file.getBytes();
            try (PDDocument document = Loader.loadPDF(pdfBytes)) {

                if (document.isEncrypted()) {
                    throw new RuntimeException("Le fichier PDF est protégé par un mot de passe et ne peut pas être lu.");
                }

                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                String text = stripper.getText(document);

                if (text == null || text.trim().isEmpty()) {
                    log.warn("PDF ne contient pas de texte extractible (probablement scanné) : {}", file.getOriginalFilename());
                    throw new RuntimeException(
                        "Le PDF ne contient pas de texte lisible. " +
                        "Si votre CV est une image scannée, convertissez-le d'abord en PDF avec du texte sélectionnable."
                    );
                }

                log.info("Texte extrait du CV '{}' : {} caractères", file.getOriginalFilename(), text.length());
                return text.trim();
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur lors de l'extraction du texte PDF : {}", e.getMessage());
            throw new RuntimeException("Impossible de lire le fichier PDF : " + e.getMessage());
        }
    }
}
