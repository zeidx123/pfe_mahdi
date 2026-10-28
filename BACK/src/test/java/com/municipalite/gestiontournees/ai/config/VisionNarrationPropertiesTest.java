package com.municipalite.gestiontournees.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class VisionNarrationPropertiesTest {
    // Same placeholders as application.properties; the test assumes these variables are not set on the machine.
    private static final String[] APPLICATION_LINES = {
            "ai.vision-narration=${VISION_NARRATION:TEMPLATE}",
            "ai.vision-narration-timeout-seconds=${VISION_NARRATION_TIMEOUT_SECONDS:10}"};

    @Test void absentVisionNarrationMeansTemplate() {
        new ApplicationContextRunner().withUserConfiguration(AiConfig.class)
                .withPropertyValues(APPLICATION_LINES)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(AiProperties.class);
                    assertThat(properties.getVisionNarration()).isEqualTo(AiProperties.VisionNarration.TEMPLATE);
                    assertThat(properties.getVisionNarrationTimeoutSeconds()).isEqualTo(10);
                    assertThat(properties.getVisionEngine()).isEqualTo(AiProperties.VisionEngine.LLM);
                });
    }

    @Test void llmNarrationBindsIndependentlyOfVisionEngine() {
        new ApplicationContextRunner().withUserConfiguration(AiConfig.class)
                .withPropertyValues("ai.vision-narration=LLM", "ai.vision-narration-timeout-seconds=4",
                        "ai.vision-engine=LOCAL")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(AiProperties.class);
                    assertThat(properties.getVisionNarration()).isEqualTo(AiProperties.VisionNarration.LLM);
                    assertThat(properties.getVisionNarrationTimeoutSeconds()).isEqualTo(4);
                    assertThat(properties.getVisionEngine()).isEqualTo(AiProperties.VisionEngine.LOCAL);
                });
    }
}
