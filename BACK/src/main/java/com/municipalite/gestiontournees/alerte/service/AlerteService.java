package com.municipalite.gestiontournees.alerte.service;

import com.municipalite.gestiontournees.alerte.model.Alerte;
import com.municipalite.gestiontournees.alerte.repository.AlerteRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlerteService {

    private final AlerteRepository alerteRepository;

    @PostConstruct
    public void initDemoAlerts() {
        if (alerteRepository.count() == 0) {
            alerteRepository.save(Alerte.builder()
                    .titre("Niveau de carburant bas")
                    .description("Le camion A-12345 est à moins de 15% de carburant.")
                    .niveau("WARNING")
                    .type("IOT")
                    .sourceId("CAM-001")
                    .resolue(false)
                    .dateCreation(LocalDateTime.now().minusHours(2))
                    .build());
            
            alerteRepository.save(Alerte.builder()
                    .titre("Température moteur anormale")
                    .description("Alerte mécanique reçue pour le véhicule B-98765.")
                    .niveau("CRITICAL")
                    .type("MECANIQUE")
                    .resolue(false)
                    .dateCreation(LocalDateTime.now().minusMinutes(30))
                    .build());
        }
    }

    public List<Alerte> getAll() {
        return alerteRepository.findAll();
    }

    public long getUnreadCount() {
        return alerteRepository.countByResolueFalse();
    }

    public Alerte markAsResolved(String id) {
        Alerte alerte = alerteRepository.findById(id).orElseThrow();
        alerte.setResolue(true);
        alerte.setDateResolution(LocalDateTime.now());
        return alerteRepository.save(alerte);
    }
}
