package com.municipalite.gestiontournees.alerte.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "alertes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alerte {
    @Id
    private String id;
    private String titre;
    private String description;
    private String niveau; // INFO, WARNING, CRITICAL
    private String type; // MECANIQUE, IOT, RETARD
    private String sourceId; // id du camion ou de la tournée
    private boolean resolue;
    private LocalDateTime dateCreation;
    private LocalDateTime dateResolution;
}
