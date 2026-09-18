package com.municipalite.gestiontournees.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class AiConfigTest {
    @Test void propertiesAreRegisteredExactlyOnceAndModelIsConfigurable() {
        new ApplicationContextRunner().withUserConfiguration(AiConfig.class)
                .withPropertyValues("ai.gemini.model-text=fixture-stable-flash", "ai.rag.embedding-model=fixture-embedding")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiProperties.class);
                    assertThat(context).hasBean("documentTaskExecutor");
                    var executor = context.getBean("documentTaskExecutor", org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class);
                    assertThat(executor.getCorePoolSize()).isEqualTo(1);
                    assertThat(executor.getMaxPoolSize()).isEqualTo(1);
                    assertThat(executor.getQueueCapacity()).isEqualTo(32);
                    assertThat(context.getBean(AiProperties.class).getGemini().getModelText()).isEqualTo("fixture-stable-flash");
                    assertThat(context.getBean(AiProperties.class).getRag().getEmbeddingModel()).isEqualTo("fixture-embedding");
                });
    }
}
