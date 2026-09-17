package com.municipalite.gestiontournees.rapport.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.municipalite.gestiontournees.pointcollecte.model.PointCollecte;
import com.municipalite.gestiontournees.pointcollecte.repository.PointCollecteRepository;
import com.municipalite.gestiontournees.tournee.dto.TourneeResponse;
import com.municipalite.gestiontournees.tournee.service.TourneeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PdfRapportService {

    private final TourneeService tourneeService;
    private final PointCollecteRepository pointCollecteRepository;

    public byte[] generateTourneeReport(String tourneeId) {
        TourneeResponse tournee = tourneeService.getById(tourneeId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            // Titre
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph title = new Paragraph("Rapport de Tournée de Collecte", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            // Infos Générales
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font textFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            
            document.add(new Paragraph("Informations Générales", subtitleFont));
            document.add(new Paragraph("Référence : " + tournee.getReference(), textFont));
            document.add(new Paragraph("Nom : " + tournee.getNom(), textFont));
            document.add(new Paragraph("Date Prévue : " + tournee.getDatePrevue(), textFont));
            document.add(new Paragraph("Statut : " + tournee.getStatut().name(), textFont));
            document.add(new Paragraph(" ", textFont)); // Espace

            // Ressources affectées
            document.add(new Paragraph("Ressources Affectées", subtitleFont));
            document.add(new Paragraph("Chauffeur/Agent : " + tournee.getChauffeurNomComplet(), textFont));
            document.add(new Paragraph("Véhicule : " + tournee.getCamionMatricule(), textFont));
            document.add(new Paragraph(" ", textFont));

            // Horaires
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            document.add(new Paragraph("Déroulement", subtitleFont));
            document.add(new Paragraph("Début réel : " + (tournee.getHeureDebutReelle() != null ? tournee.getHeureDebutReelle().format(formatter) : "N/A"), textFont));
            document.add(new Paragraph("Fin réelle : " + (tournee.getHeureFinReelle() != null ? tournee.getHeureFinReelle().format(formatter) : "N/A"), textFont));
            if (tournee.getNotes() != null && !tournee.getNotes().isEmpty()) {
                document.add(new Paragraph("Notes : " + tournee.getNotes(), textFont));
            }
            document.add(new Paragraph(" ", textFont));

            // Tableau des points de collecte
            document.add(new Paragraph("Points de Collecte Visités", subtitleFont));
            document.add(new Paragraph(" ", textFont));

            if (tournee.getPointsCollecteIds() != null && !tournee.getPointsCollecteIds().isEmpty()) {
                PdfPTable table = new PdfPTable(5);
                table.setWidthPercentage(100);
                table.setSpacingBefore(10f);
                table.setWidths(new float[]{1.5f, 2.5f, 1.5f, 1.2f, 1.8f});

                // Header
                addTableHeader(table, "Référence", textFont);
                addTableHeader(table, "Adresse", textFont);
                addTableHeader(table, "Type Déchet", textFont);
                addTableHeader(table, "Remplissage", textFont);
                addTableHeader(table, "Photo Preuve", textFont);

                // Lignes
                List<PointCollecte> points = (List<PointCollecte>) pointCollecteRepository.findAllById(tournee.getPointsCollecteIds());
                for (PointCollecte point : points) {
                    addTableCell(table, point.getReference(), textFont);
                    addTableCell(table, point.getAdresse(), textFont);
                    addTableCell(table, point.getTypeDechet() != null ? point.getTypeDechet().name() : "-", textFont);
                    addTableCell(table, point.getNiveauRemplissage() + "%", textFont);
                    
                    // Embed Point photo
                    String photoName = tournee.getPhotosPoints() != null ? tournee.getPhotosPoints().get(point.getId()) : null;
                    if (photoName != null && !photoName.isEmpty()) {
                        addTableCellImage(table, "uploads/photos/" + photoName);
                    } else {
                        addTableCell(table, "Non validé", textFont);
                    }
                }
                
                document.add(table);
            } else {
                document.add(new Paragraph("Aucun point de collecte enregistré pour cette tournée.", textFont));
            }

            document.add(new Paragraph(" ", textFont));

            // Photo de fin de tournée
            if (tournee.getPhotoFin() != null && !tournee.getPhotoFin().isEmpty()) {
                document.add(new Paragraph("Photo de Fin de Tournée", subtitleFont));
                document.add(new Paragraph(" ", textFont));
                try {
                    Image imgFin = Image.getInstance("uploads/photos/" + tournee.getPhotoFin());
                    imgFin.scaleToFit(300, 200);
                    imgFin.setAlignment(Element.ALIGN_CENTER);
                    document.add(imgFin);
                } catch (Exception e) {
                    document.add(new Paragraph("[Image fin indisponible ou erreur de chargement]", textFont));
                }
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération du PDF", e);
        }
    }

    private void addTableHeader(PdfPTable table, String headerTitle, Font font) {
        PdfPCell header = new PdfPCell();
        header.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
        header.setBorderWidth(1);
        header.setPhrase(new Phrase(headerTitle, font));
        header.setPadding(5);
        table.addCell(header);
    }

    private void addTableCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addTableCellImage(PdfPTable table, String imagePath) {
        try {
            Image img = Image.getInstance(imagePath);
            img.scaleToFit(60, 45); // format miniature
            PdfPCell cell = new PdfPCell(img);
            cell.setPadding(3);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell);
        } catch (Exception e) {
            PdfPCell cell = new PdfPCell(new Phrase("Image erreur"));
            cell.setPadding(5);
            table.addCell(cell);
        }
    }
}
