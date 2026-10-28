package com.municipalite.gestiontournees.ai.vision.narrative;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The only data a language model receives about a LOCAL analysis: no photo, identifier, name, coordinate,
 * driver or free text. Percentages are integers; sharpness and brightness are rounded to one decimal.
 */
public record NarrativeFacts(String predictedClass, int propre, int debordement, int indetermine,
                             String qualityIssue, double sharpness, double brightness,
                             String decision, String inputMode) {

    public int predictedPercent() {
        return switch (predictedClass) {
            case "PROPRE" -> propre;
            case "DEBORDEMENT" -> debordement;
            default -> indetermine;
        };
    }

    /** French rendering sent to the model (prompt vision-narrative-v1). */
    public Map<String, Object> forModel() {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("classe_predite", switch (predictedClass) {
            case "PROPRE" -> "propre";
            case "DEBORDEMENT" -> "débordement";
            default -> "indéterminé";
        });
        Map<String, Object> percentages = new LinkedHashMap<>();
        percentages.put("propre", propre);
        percentages.put("débordement", debordement);
        percentages.put("indéterminé", indetermine);
        facts.put("pourcentages", percentages);
        facts.put("decision", "CONFIRMATION_AUTO".equals(decision) ? "confirmation automatique" : "à vérifier");
        facts.put("probleme_qualite", qualityIssue == null ? null : switch (qualityIssue) {
            case "TROP_SOMBRE" -> "trop sombre";
            case "SUREXPOSEE" -> "surexposée";
            default -> "floue";
        });
        facts.put("nettete", sharpness);
        facts.put("luminosite", brightness);
        facts.put("photos", "BEFORE_AFTER".equals(inputMode)
                ? "photos AVANT et APRÈS fournies, seule la photo APRÈS est analysée" : "photo APRÈS seule");
        return facts;
    }

    public static double oneDecimal(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
