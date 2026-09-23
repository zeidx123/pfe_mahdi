package com.municipalite.gestiontournees.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class AiConfigTest {
    @Test void groqSelectionAndModelsBindIndependentlyOfGeminiAndOpenRouter() {
        new ApplicationContextRunner().withUserConfiguration(AiConfig.class)
                .withPropertyValues("ai.collection-provider=GROQ", "ai.groq.api-key=fixture-groq-key",
                        "ai.groq.model-text=qwen/qwen3.8-27b", "ai.groq.model-vision=qwen/qwen3.8-27b",
                        "ai.groq.timeout-seconds=50", "ai.gemini.model-text=fixture-chat-model")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(AiProperties.class);
                    assertThat(properties.getCollectionProvider()).isEqualTo(AiProperties.CollectionProvider.GROQ);
                    assertThat(properties.getGroq().getApiKey()).isEqualTo("fixture-groq-key");
                    assertThat(properties.getGroq().getTimeoutSeconds()).isEqualTo(50);
                    assertThat(properties.getCollectionTextModel()).isEqualTo("qwen/qwen3.8-27b");
                    assertThat(properties.getCollectionVisionModel()).isEqualTo("qwen/qwen3.8-27b");
                    assertThat(properties.getGemini().getModelText()).isEqualTo("fixture-chat-model");
                    assertThat(properties.getOpenRouter().getApiKey()).isNull();
                });
    }

    @Test void propertiesAreRegisteredExactlyOnceAndModelIsConfigurable() {
        new ApplicationContextRunner().withUserConfiguration(AiConfig.class)
                .withPropertyValues("ai.gemini.model-text=fixture-stable-flash", "ai.rag.embedding-model=fixture-embedding",
                        "ai.open-router.model-text=qwen/qwen3.8-27b:free", "ai.open-router.model-vision=qwen/qwen3.8-27b:free",
                        "ai.open-router.api-key=fixture-key", "ai.open-router.timeout-seconds=45")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiProperties.class);
                    assertThat(context).hasBean("documentTaskExecutor");
                    var executor = context.getBean("documentTaskExecutor", org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class);
                    assertThat(executor.getCorePoolSize()).isEqualTo(1);
                    assertThat(executor.getMaxPoolSize()).isEqualTo(1);
                    assertThat(executor.getQueueCapacity()).isEqualTo(32);
                    assertThat(context.getBean(AiProperties.class).getGemini().getModelText()).isEqualTo("fixture-stable-flash");
                    assertThat(context.getBean(AiProperties.class).getRag().getEmbeddingModel()).isEqualTo("fixture-embedding");
                    assertThat(context.getBean(AiProperties.class).getOpenRouter().getModelText()).isEqualTo("qwen/qwen3.8-27b:free");
                    assertThat(context.getBean(AiProperties.class).getOpenRouter().getModelVision()).isEqualTo("qwen/qwen3.8-27b:free");
                    assertThat(context.getBean(AiProperties.class).getOpenRouter().getApiKey()).isEqualTo("fixture-key");
                    assertThat(context.getBean(AiProperties.class).getOpenRouter().getTimeoutSeconds()).isEqualTo(45);
                });
    }
}
