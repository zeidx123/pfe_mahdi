package com.municipalite.gestiontournees.ai.cv.dto;

import com.municipalite.gestiontournees.ai.cv.model.FieldEvidence;
import lombok.Data;
import java.util.Map;

@Data
public class CandidateReviewRequest {
    private Map<String, FieldEvidence> fields;
}
