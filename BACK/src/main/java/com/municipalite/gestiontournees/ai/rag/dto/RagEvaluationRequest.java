package com.municipalite.gestiontournees.ai.rag.dto;

import com.municipalite.gestiontournees.ai.rag.service.VectorStoreService.RetrievalMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

/** Explicit evaluation input; never creates or changes a driver's conversation. */
@Data
public class RagEvaluationRequest {
    @NotBlank @Size(max = 2000)
    private String question;
    @NotNull @Size(min = 1, max = 3)
    private List<@NotNull RetrievalMode> modes = List.of(RetrievalMode.LEXICAL, RetrievalMode.SEMANTIC, RetrievalMode.HYBRID);
    @NotNull @Size(max = 6) @Valid
    private List<HistoryTurn> history = List.of();
    private boolean generateAnswer;
    @Pattern(regexp = "[a-f0-9]{64}")
    private String expectedCorpusFingerprint;

    public record HistoryTurn(@NotNull @Pattern(regexp = "USER|ASSISTANT") String sender,
                              @NotBlank @Size(max = 12000) String content) {}
}
