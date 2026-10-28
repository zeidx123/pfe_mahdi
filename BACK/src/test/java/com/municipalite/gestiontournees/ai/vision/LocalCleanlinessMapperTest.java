package com.municipalite.gestiontournees.ai.vision;

import com.municipalite.gestiontournees.ai.common.LocalVisionClient;
import com.municipalite.gestiontournees.ai.vision.service.LocalCleanlinessMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalCleanlinessMapperTest {
    private static final String SHA = "da40e684d2ddc268ae3c976e668dcd2a9861a5e42c46d6143a1dea27b1bbef97";

    /** classe prédite | p(PROPRE) | défaut de qualité | seuil (vide = non défini) | statut | décision */
    @ParameterizedTest(name = "{0} p={1} qualité={2} seuil={3} -> {4} {5}")
    @CsvSource(nullValues = "-", value = {
            // seuil défini : juste au-dessus, égal, juste au-dessous
            "PROPRE,           0.801, -,           0.8, CLEANED,          CONFIRMATION_AUTO",
            "PROPRE,           0.800, -,           0.8, CLEANED,          CONFIRMATION_AUTO",
            "PROPRE,           0.799, -,           0.8, CLEANED,          A_VERIFIER",
            // seuil non défini : toujours à vérifier
            "PROPRE,           0.999, -,           -,   CLEANED,          A_VERIFIER",
            // seuil hors de ]0, 1] : ignoré
            "PROPRE,           0.999, -,           0.0, CLEANED,          A_VERIFIER",
            "PROPRE,           0.999, -,           1.5, CLEANED,          A_VERIFIER",
            // classe autre que PROPRE : jamais de confirmation automatique, même au-dessus du seuil
            "DEBORDEMENT,      0.100, -,           0.05, NOT_CLEANED,     A_VERIFIER",
            "DEBORDEMENT,      0.100, -,           -,   NOT_CLEANED,      A_VERIFIER",
            "CANNOT_DETERMINE, 0.100, -,           0.05, CANNOT_DETERMINE, A_VERIFIER",
            "CANNOT_DETERMINE, 0.100, -,           -,   CANNOT_DETERMINE, A_VERIFIER",
            // porte de qualité refusée : CANNOT_DETERMINE et à vérifier, quelle que soit la sortie du modèle
            "PROPRE,           0.999, FLOUE,       0.8, CANNOT_DETERMINE, A_VERIFIER",
            "PROPRE,           0.999, TROP_SOMBRE, -,   CANNOT_DETERMINE, A_VERIFIER",
            "DEBORDEMENT,      0.100, SUREXPOSEE,  0.8, CANNOT_DETERMINE, A_VERIFIER",
            "CANNOT_DETERMINE, 0.100, FLOUE,       -,   CANNOT_DETERMINE, A_VERIFIER"})
    void statusAndDecisionTable(String predicted, double propre, String issue, Double threshold,
                                String status, String decision) {
        var result = LocalCleanlinessMapper.map(prediction(predicted, propre, issue), threshold);
        assertThat(result.status()).isEqualTo(status).isNotEqualTo("OBSTRUCTED");
        assertThat(result.decision()).isEqualTo(decision);
        assertThat(result.description()).isNotBlank().hasSizeLessThanOrEqualTo(2000)
                .doesNotContainIgnoringCase("prouv").doesNotContainIgnoringCase("chauffeur");
    }

    @Test void templatesUseIntegerPercentagesInFrench() {
        var propre = LocalCleanlinessMapper.map(prediction("PROPRE", 0.91, null, 0.02, 0.07), null);
        assertThat(propre.description()).isEqualTo("Analyse automatique locale (modèle proprete_mobilenetv3l) de la photo APRÈS : "
                + "point probablement propre. Probabilités : propre 91 %, débordement 7 %, indéterminé 2 %. "
                + "À confirmer par un contrôle humain.");
        var debordement = LocalCleanlinessMapper.map(prediction("DEBORDEMENT", 0.10, null, 0.05, 0.85), null);
        assertThat(debordement.description()).contains("point probablement non nettoyé (débordement ou déchets restants)")
                .contains("propre 10 %, débordement 85 %, indéterminé 5 %").endsWith("À confirmer par un contrôle humain.");
        var incertain = LocalCleanlinessMapper.map(prediction("CANNOT_DETERMINE", 0.10, null, 0.80, 0.10), null);
        assertThat(incertain.description()).contains("photo difficile à interpréter par le modèle")
                .endsWith("Reprendre la photo en cadrant le point est recommandé.");
    }

    @Test void qualityRefusalTemplate() {
        var result = LocalCleanlinessMapper.map(prediction("PROPRE", 0.99, "TROP_SOMBRE"), 0.5);
        assertThat(result.description()).isEqualTo("Photo APRÈS trop sombre pour être analysée (netteté 12,4 ; luminosité 31). "
                + "Reprenez la photo en cadrant le point.");
        assertThat(LocalCleanlinessMapper.map(prediction("PROPRE", 0.99, "FLOUE"), 0.5).description()).startsWith("Photo APRÈS floue");
        assertThat(LocalCleanlinessMapper.map(prediction("PROPRE", 0.99, "SUREXPOSEE"), 0.5).description()).startsWith("Photo APRÈS surexposée");
    }

    private static LocalVisionClient.Prediction prediction(String predicted, double propre, String issue) {
        double rest = (1 - propre) / 2;
        return prediction(predicted, propre, issue, rest, rest);
    }

    private static LocalVisionClient.Prediction prediction(String predicted, double propre, String issue,
                                                           double cannotDetermine, double debordement) {
        Map<String, Double> probabilities = new LinkedHashMap<>();
        probabilities.put("CANNOT_DETERMINE", cannotDetermine);
        probabilities.put("DEBORDEMENT", debordement);
        probabilities.put("PROPRE", propre);
        return new LocalVisionClient.Prediction(probabilities, predicted, 12.43, 31.2, issue, "proprete_mobilenetv3l", SHA);
    }
}
