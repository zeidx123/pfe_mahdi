package com.municipalite.gestiontournees.ai.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception levée lorsqu'un service IA est indisponible ou retourne une erreur.
 * Mappée sur HTTP 503 Service Unavailable.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class AiServiceException extends RuntimeException {
    private final String category;
    private final Integer providerHttpStatus;

    public AiServiceException(String message) {
        this(message, "PROVIDER_UNAVAILABLE", null, null);
    }

    public AiServiceException(String message, Throwable cause) {
        this(message, "PROVIDER_UNAVAILABLE", null, cause);
    }

    public AiServiceException(String message, String category, Integer providerHttpStatus, Throwable cause) {
        super(message, cause);
        this.category = category;
        this.providerHttpStatus = providerHttpStatus;
    }

    public String getCategory() { return category; }
    public Integer getProviderHttpStatus() { return providerHttpStatus; }
}
