package com.municipalite.gestiontournees.ai.route.service;

import com.municipalite.gestiontournees.ai.route.dto.OptimizationRequest;
import com.municipalite.gestiontournees.ai.route.dto.OptimizationResponse;
import com.municipalite.gestiontournees.ai.route.model.RouteOptimization;
import com.municipalite.gestiontournees.ai.route.repository.RouteOptimizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/** Legacy proposals remain readable; all new calculations belong to persisted tours. */
@Service @RequiredArgsConstructor
public class RouteOptimizationService {
    private final RouteOptimizationRepository routeOptimizationRepository;
    public OptimizationResponse optimize(OptimizationRequest request) {
        throw retired();
    }
    public RouteOptimization validateOptimization(String id, String statut) {
        throw retired();
    }
    public List<RouteOptimization> getAll() { return routeOptimizationRepository.findAllByOrderByDateCreationDesc(); }
    private ResponseStatusException retired() {
        return new ResponseStatusException(HttpStatus.GONE,
                "Utilisez POST /api/tournees/{id}/optimiser. Les anciennes propositions sont conservées en lecture seule.");
    }
}
