package com.municipalite.gestiontournees.tournee.service;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Data @Component @ConfigurationProperties(prefix = "routing")
public class RoutingProperties {
    private String apiKey = "";
    private String baseUrl = "https://api.openrouteservice.org";
    private String profile = "driving-car";
    private Double depotLatitude;
    private Double depotLongitude;
    private String depotLabel = "Dépôt";
    private int maxPoints = 20;
    private int serviceSeconds = 300;
}
