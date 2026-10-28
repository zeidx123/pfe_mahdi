package com.municipalite.gestiontournees.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class LocalVisionPropertiesTest {
    // Same placeholders as application.properties; the test assumes these variables are not set on the machine.
    private static final String[] APPLICATION_LINES = {
            "ai.vision-engine=${VISION_ENGINE:LLM}",
            "ai.local-vision.base-url=${LOCAL_VISION_URL:http://127.0.0.1:8090}",
            "ai.local-vision.timeout-seconds=${LOCAL_VISION_TIMEOUT_SECONDS:15}",
            "ai.local-vision.token=${LOCAL_VISION_TOKEN:}",
            "ai.local-vision.propre-threshold=${VISION_PROPRE_THRESHOLD:}",
            "ai.local-vision.min-sharpness=${VISION_MIN_SHARPNESS:}",
            "ai.local-vision.min-brightness=${VISION_MIN_BRIGHTNESS:}",
            "ai.local-vision.max-brightness=${VISION_MAX_BRIGHTNESS:}"};

    @Test void absentVisionEngineMeansLlmAndEmptyThresholdsAreAccepted() {
        new ApplicationContextRunner().withUserConfiguration(AiConfig.class)
                .withPropertyValues(APPLICATION_LINES)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(AiProperties.class);
                    assertThat(properties.getVisionEngine()).isEqualTo(AiProperties.VisionEngine.LLM);
                    assertThat(properties.getCollectionProvider()).isEqualTo(AiProperties.CollectionProvider.GROQ);
                    assertThat(properties.getLocalVision().getBaseUrl()).isEqualTo("http://127.0.0.1:8090");
                    assertThat(properties.getLocalVision().getTimeoutSeconds()).isEqualTo(15);
                    assertThat(properties.getLocalVision().getToken()).isNullOrEmpty();
                    assertThat(properties.getLocalVision().getPropreThreshold()).isNull();
                    assertThat(properties.getLocalVision().getMinSharpness()).isNull();
                    assertThat(properties.getLocalVision().getMinBrightness()).isNull();
                    assertThat(properties.getLocalVision().getMaxBrightness()).isNull();
                });
    }

    @Test void localEngineAndThresholdsBindIndependentlyOfCollectionProvider() {
        new ApplicationContextRunner().withUserConfiguration(AiConfig.class)
                .withPropertyValues("ai.vision-engine=LOCAL", "ai.collection-provider=OPENROUTER",
                        "ai.local-vision.base-url=http://127.0.0.1:9999", "ai.local-vision.timeout-seconds=9",
                        "ai.local-vision.propre-threshold=0.9", "ai.local-vision.min-sharpness=118.68",
                        "ai.local-vision.min-brightness=30", "ai.local-vision.max-brightness=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(AiProperties.class);
                    assertThat(properties.getVisionEngine()).isEqualTo(AiProperties.VisionEngine.LOCAL);
                    assertThat(properties.getCollectionProvider()).isEqualTo(AiProperties.CollectionProvider.OPENROUTER);
                    assertThat(properties.getLocalVision().getBaseUrl()).isEqualTo("http://127.0.0.1:9999");
                    assertThat(properties.getLocalVision().getTimeoutSeconds()).isEqualTo(9);
                    assertThat(properties.getLocalVision().getPropreThreshold()).isEqualTo(0.9);
                    assertThat(properties.getLocalVision().getMinSharpness()).isEqualTo(118.68);
                    assertThat(properties.getLocalVision().getMinBrightness()).isEqualTo(30.0);
                    assertThat(properties.getLocalVision().getMaxBrightness()).isNull();
                });
    }
}
