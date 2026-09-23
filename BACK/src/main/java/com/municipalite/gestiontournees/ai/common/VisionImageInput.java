package com.municipalite.gestiontournees.ai.common;

/** Validated image bytes, kept in evidence order for either vision provider. */
public record VisionImageInput(byte[] bytes, String mimeType) {}
