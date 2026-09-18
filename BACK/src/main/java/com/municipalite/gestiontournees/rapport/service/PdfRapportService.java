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
    @org.springframework.beans.factory.annotation.Value("${ai.storage.photos-dir:uploads/photos}")
    private String photoDirectory = "uploads/photos";

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

            if (tournee.getRoute() != null) {
                document.add(new Paragraph("Itinéraire routier : " + tournee.getRoute().getAlgorithm() + " — départ et retour au dépôt", textFont));
                document.add(new Paragraph("Distance estimée : " + String.format(java.util.Locale.ROOT, "%.2f km", tournee.getRoute().getDistanceMeters() / 1000.0), textFont));
                document.add(new Paragraph("Durée estimée au départ : " + seconds(tournee.getEstimatedAtStartSeconds()), textFont));
                document.add(new Paragraph("Durée réelle totale : " + seconds(tournee.getActualDurationSeconds()), textFont));
                document.add(new Paragraph("Service observé : " + seconds(tournee.getObservedServiceSeconds()) + " (" + tournee.getMeasuredServiceStops() + " arrêts mesurés)", textFont));
                document.add(new Paragraph("Les estimations excluent le trafic en temps réel. La durée réelle inclut les pauses éventuelles.", textFont));
                document.add(new Paragraph(" ", textFont));
            }

            // Tableau des points de collecte
            document.add(new Paragraph("Points et résultats déclarés", subtitleFont));
            document.add(new Paragraph(" ", textFont));

            if (tournee.getPointsCollecteIds() != null && !tournee.getPointsCollecteIds().isEmpty()) {
                PdfPTable table = new PdfPTable(5);
                table.setWidthPercentage(100);
                table.setSpacingBefore(10f);
                table.setWidths(new float[]{1.5f, 2.5f, 1.5f, 1.2f, 1.8f});

                // Header
                addTableHeader(table, "Référence", textFont);
                addTableHeader(table, "Adresse", textFont);
                addTableHeader(table, "Visite", textFont);
                addTableHeader(table, "IA / Revue", textFont);
                addTableHeader(table, "Photo APRÈS", textFont);
                table.setHeaderRows(1);

                // Lignes
                for (var point : tournee.getPointsCollecte()) {
                    var visit = tournee.getVisits().stream().filter(v -> point.getId().equals(v.getPointId())).findFirst().orElse(null);
                    addTableCell(table, point.getReference(), textFont);
                    addTableCell(table, point.getAdresse(), textFont);
                    addTableCell(table, visit == null || visit.isLegacyProgressMissing() ? "Historique non renseigné" : visit.getStatus().name()
                            + (visit.getNotes() == null ? "" : "\n" + visit.getNotes()), textFont);
                    addTableCell(table, visit == null ? "Non analysé" : "IA : " + (visit.getAnalysisStatus() == null ? "Non analysé" : visit.getAnalysisStatus())
                            + "\nRevue : " + (visit.getReviewLabel() == null ? "À examiner" : visit.getReviewLabel()), textFont);
                    
                    // Embed Point photo
                    String photoName = visit == null ? null : visit.getAfterPhoto();
                    if (photoName != null && !photoName.isEmpty()) {
                        addTableCellImage(table, java.nio.file.Path.of(photoDirectory, photoName).toString());
                    } else {
                        addTableCell(table, visit != null && visit.getLegacyPhoto() != null
                                ? "Étape non enregistrée pour la photo historique" : "Non fournie", textFont);
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
                    Image imgFin = Image.getInstance(java.nio.file.Path.of(photoDirectory, tournee.getPhotoFin()).toString());
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

    private String seconds(Number value) {
        return value == null ? "Non mesurée" : String.format(java.util.Locale.ROOT, "%.1f min", value.doubleValue() / 60.0);
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
