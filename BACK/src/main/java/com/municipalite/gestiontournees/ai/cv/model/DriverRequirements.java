package com.municipalite.gestiontournees.ai.cv.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Document(collection = "driver_requirement_versions")
public class DriverRequirements {
    @Id private String id;
    private int version;
    private String label;
    private List<String> acceptedLicences;
    private int licenceWeight;
    private boolean licenceRequired;
    private double experienceTargetYears;
    private int experienceWeight;
    private boolean experienceRequired;
    private int safetyWeight;
    private boolean safetyRequired;
    private int collectionWeight;
    private boolean collectionRequired;
    private String editedBy;
    private LocalDateTime editedAt;

    public static DriverRequirements initial() {
        return DriverRequirements.builder().id("driver-profile-v1").version(1).label("Chauffeur de collecte")
                .acceptedLicences(List.of("C", "CE")).licenceWeight(40).licenceRequired(true)
                .experienceTargetYears(2).experienceWeight(40).experienceRequired(false)
                .safetyWeight(10).collectionWeight(10).build();
    }
}
