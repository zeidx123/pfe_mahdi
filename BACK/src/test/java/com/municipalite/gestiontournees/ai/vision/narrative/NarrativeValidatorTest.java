package com.municipalite.gestiontournees.ai.vision.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Shared vectors, also read by scripts/evaluation/test_narrative_validator.py. */
class NarrativeValidatorTest {
    private final NarrativeValidator validator = new NarrativeValidator();

    @Test void sharedVectorsGiveTheExpectedCodes() throws Exception {
        JsonNode cases = new ObjectMapper().readTree(new ClassPathResource("vision/narrative_validator_cases.json").getInputStream()).path("cas");
        assertThat(cases.size()).isGreaterThanOrEqualTo(30);
        Set<String> covered = new TreeSet<>();
        List<String> mismatches = new ArrayList<>();
        for (JsonNode c : cases) {
            List<String> expected = new ArrayList<>();
            c.path("motifs").forEach(m -> expected.add(m.asText()));
            covered.addAll(expected);
            List<String> actual = validator.validate(c.path("texte").asText(), facts(c.path("donnees")));
            if (!actual.equals(expected)) mismatches.add(c.path("id").asText() + " attendu " + expected + " obtenu " + actual);
            assertThat(c.path("attendu").asText()).isEqualTo(expected.isEmpty() ? "accepte" : "rejete");
        }
        assertThat(mismatches).isEmpty();
        assertThat(covered).containsExactly("V1", "V2", "V3", "V4", "V5", "V6", "V7");
    }

    @Test void nullTextIsEmpty() {
        assertThat(validator.validate(null, new NarrativeFacts("PROPRE", 91, 7, 2, null, 412.5, 120.3, null, null)))
                .containsExactly("V1");
    }

    static NarrativeFacts facts(JsonNode d) {
        JsonNode p = d.path("pourcentages");
        return new NarrativeFacts(d.path("classe_predite").asText(), p.path("PROPRE").asInt(), p.path("DEBORDEMENT").asInt(),
                p.path("CANNOT_DETERMINE").asInt(), d.path("probleme_qualite").isNull() ? null : d.path("probleme_qualite").asText(),
                d.path("nettete").asDouble(), d.path("luminosite").asDouble(), null, null);
    }
}
