package com.municipalite.gestiontournees.ai.vision.service;

import com.municipalite.gestiontournees.ai.common.LocalVisionClient;

import java.util.Locale;

/**
 * Maps the local model output onto the existing observation labels, without any new status value:
 * PROPRE -> CLEANED, DEBORDEMENT -> NOT_CLEANED, CANNOT_DETERMINE -> CANNOT_DETERMINE, and a photo refused by the
 * quality gate -> CANNOT_DETERMINE. OBSTRUCTED is never produced here: it stays a human label.
 *
 * The decision only orders human review. It never changes the visit state or the end of the tour, and it is
 * not one of the observation labels (TourneeService.HUMAN_LABELS). The model precision for PROPRE is about 0.71
 * on the test set (training labels partly extrapolated, nothing validated in the field), hence no default threshold.
 */
public final class LocalCleanlinessMapper {
    public static final String CONFIRMATION_AUTO = "CONFIRMATION_AUTO";
    public static final String A_VERIFIER = "A_VERIFIER";
    private static final String PREFIX = "Analyse automatique locale (modèle proprete_mobilenetv3l) de la photo APRÈS : ";

    public record Result(String status, String decision, String description) {}

    private LocalCleanlinessMapper() {}

    /** @param propreThreshold null (or outside ]0, 1]) means no automatic confirmation: always A_VERIFIER. */
    public static Result map(LocalVisionClient.Prediction prediction, Double propreThreshold) {
        if (prediction.qualityIssue() != null) {
            // The model was still called; its probabilities are stored but change neither status nor decision.
            return new Result("CANNOT_DETERMINE", A_VERIFIER, qualityDescription(prediction));
        }
        double propre = prediction.probabilities().get("PROPRE");
        boolean thresholdSet = propreThreshold != null && propreThreshold > 0 && propreThreshold <= 1;
        String decision = "PROPRE".equals(prediction.predictedClass()) && thresholdSet && propre >= propreThreshold
                ? CONFIRMATION_AUTO : A_VERIFIER;
        return switch (prediction.predictedClass()) {
            case "PROPRE" -> new Result("CLEANED", decision, PREFIX + "point probablement propre. "
                    + probabilities(prediction) + " À confirmer par un contrôle humain.");
            case "DEBORDEMENT" -> new Result("NOT_CLEANED", decision, PREFIX
                    + "point probablement non nettoyé (débordement ou déchets restants). "
                    + probabilities(prediction) + " À confirmer par un contrôle humain.");
            default -> new Result("CANNOT_DETERMINE", decision, PREFIX + "photo difficile à interpréter par le modèle. "
                    + probabilities(prediction) + " Reprendre la photo en cadrant le point est recommandé.");
        };
    }

    private static String probabilities(LocalVisionClient.Prediction prediction) {
        return "Probabilités : propre " + percent(prediction, "PROPRE") + ", débordement " + percent(prediction, "DEBORDEMENT")
                + ", indéterminé " + percent(prediction, "CANNOT_DETERMINE") + ".";
    }

    private static String percent(LocalVisionClient.Prediction prediction, String label) {
        return Math.round(prediction.probabilities().get(label) * 100) + " %";
    }

    private static String qualityDescription(LocalVisionClient.Prediction prediction) {
        String reason = switch (prediction.qualityIssue()) {
            case "TROP_SOMBRE" -> "trop sombre";
            case "SUREXPOSEE" -> "surexposée";
            default -> "floue";
        };
        return String.format(Locale.FRANCE, "Photo APRÈS %s pour être analysée (netteté %.1f ; luminosité %.0f). "
                + "Reprenez la photo en cadrant le point.", reason, prediction.sharpness(), prediction.meanBrightness());
    }
}
