package com.municipalite.gestiontournees.ai.rag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagQueryRequest {
    private String conversationId;

    @NotBlank(message = "La question est obligatoire")
    private String question;
}
