package com.municipalite.gestiontournees.ai.vision.narrative;

/** Text-only generator: it receives a prompt built from structured data and never sees the photo. */
public interface NarrativeGenerator {
    /** @return the raw JSON answer {"texte": "..."}; failures are AiServiceException with an existing category. */
    String generate(String prompt);

    /** Provider and model used, for traceability (never a credential). */
    String modelId();
}
