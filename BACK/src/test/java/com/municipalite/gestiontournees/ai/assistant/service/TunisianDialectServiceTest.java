package com.municipalite.gestiontournees.ai.assistant.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TunisianDialectServiceTest {

    private final TunisianDialectService dialectService = new TunisianDialectService();

    @Test
    void testDetectLatinDerjaArabizi() {
        assertThat(dialectService.detectLanguageStyle("Kifech najm nchouf el tournee mte3i ?"))
                .isEqualTo(TunisianDialectService.LanguageStyle.DERJA_LATIN);

        assertThat(dialectService.detectLanguageStyle("Chnowa naamel ken el bac mkasser ?"))
                .isEqualTo(TunisianDialectService.LanguageStyle.DERJA_LATIN);

        assertThat(dialectService.detectLanguageStyle("brabi 3aweni kifech nbadal"))
                .isEqualTo(TunisianDialectService.LanguageStyle.DERJA_LATIN);
    }

    @Test
    void testDetectArabicScriptDerja() {
        assertThat(dialectService.detectLanguageStyle("كيفاش نعمل باش نشوف tournée متاعي؟"))
                .isEqualTo(TunisianDialectService.LanguageStyle.DERJA_ARABIC);

        assertThat(dialectService.detectLanguageStyle("شنوة نعمل كان نلقى باك مكسر؟"))
                .isEqualTo(TunisianDialectService.LanguageStyle.DERJA_ARABIC);
    }

    @Test
    void testDetectFrench() {
        assertThat(dialectService.detectLanguageStyle("Comment signaler un problème pendant la tournée ?"))
                .isEqualTo(TunisianDialectService.LanguageStyle.FRENCH);

        assertThat(dialectService.detectLanguageStyle("Quelle est la procédure en cas d'accident ?"))
                .isEqualTo(TunisianDialectService.LanguageStyle.FRENCH);
    }

    @Test
    void testIsArabicScriptRtl() {
        assertThat(TunisianDialectService.isArabicScript("كيفاش نعمل باش نشوف")).isTrue();
        assertThat(TunisianDialectService.isArabicScript("Kifech najm nchouf el tournee mte3i ?")).isFalse();
        assertThat(TunisianDialectService.isArabicScript("Bonjour l'équipe")).isFalse();
    }

    @Test
    void testNoRelevantContextMessageAdaptation() {
        String msgArabizi = dialectService.getNoRelevantContextMessage("Kifech naamel m3a el tournee?");
        assertThat(msgArabizi).contains("Ma l9itech el ma3louma hedhi fel documents");

        String msgArabic = dialectService.getNoRelevantContextMessage("كيفاش نعمل؟");
        assertThat(msgArabic).contains("ما لقيتش المعلومة هذي في الوثائق");

        String msgFrench = dialectService.getNoRelevantContextMessage("Quelle est la règle ?");
        assertThat(msgFrench).contains("Je n'ai pas trouvé d'information suffisante");
    }
}
