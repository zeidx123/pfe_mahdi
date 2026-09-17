package com.municipalite.gestiontournees.tracking.controller;

import com.municipalite.gestiontournees.tracking.model.VehiculePosition;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@RestController
@RequiredArgsConstructor
public class TrackingController {

    private final SimpMessagingTemplate messagingTemplate;

    // Stockage en mémoire des dernières positions (pour l'état initial)
    private final Map<String, VehiculePosition> latestPositions = new HashMap<>();

    /**
     * Reçoit la position GPS depuis l'ESP32 via REST
     * et la diffuse via WebSocket à tous les superviseurs
     */
    @PostMapping("/api/tracking/position")
    public void receivePosition(@RequestBody VehiculePosition position) {
        position.setTimestamp(LocalDateTime.now());
        latestPositions.put(position.getCamionId(), position);

        // Diffuser en temps réel via WebSocket
        messagingTemplate.convertAndSend("/topic/positions", position);
    }

    /**
     * Retourne les dernières positions de tous les véhicules actifs
     * (pour initialiser la carte au chargement de la page)
     */
    @GetMapping("/api/tracking/positions")
    public List<VehiculePosition> getAllPositions() {
        return new ArrayList<>(latestPositions.values());
    }

    /**
     * Point d'entrée WebSocket - un client peut aussi envoyer sa position
     */
    @MessageMapping("/position")
    @SendTo("/topic/positions")
    public VehiculePosition handlePositionMessage(VehiculePosition position) {
        position.setTimestamp(LocalDateTime.now());
        latestPositions.put(position.getCamionId(), position);
        return position;
    }
}
