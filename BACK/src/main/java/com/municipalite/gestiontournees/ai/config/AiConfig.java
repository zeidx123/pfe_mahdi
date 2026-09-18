package com.municipalite.gestiontournees.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.concurrent.TimeUnit;

/**
 * Configuration Spring pour les beans partagés entre tous les agents IA.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    /**
     * Client HTTP OkHttp partagé — utilisé pour appeler l'API Gemini.
     * Le timeout est configurable via application.properties.
     */
    @Bean
    public OkHttpClient okHttpClient(AiProperties aiProperties) {
        long timeout = aiProperties.getGemini().getTimeoutSeconds();
        return new OkHttpClient.Builder()
                .callTimeout(timeout, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .connectTimeout(Math.min(timeout, 10), TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .build();
    }

    /** One bounded worker for the existing document indexing task. */
    @Bean(name = "documentTaskExecutor")
    public org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor documentTaskExecutor() {
        var executor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("rag-document-");
        return executor;
    }

    /**
     * ObjectMapper Jackson partagé pour la sérialisation/désérialisation JSON.
     */
    @Bean(name = "aiObjectMapper")
    public ObjectMapper aiObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
