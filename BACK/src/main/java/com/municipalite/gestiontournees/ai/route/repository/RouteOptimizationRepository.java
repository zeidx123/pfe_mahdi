package com.municipalite.gestiontournees.ai.route.repository;

import com.municipalite.gestiontournees.ai.route.model.RouteOptimization;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteOptimizationRepository extends MongoRepository<RouteOptimization, String> {
    List<RouteOptimization> findByCamionIdOrderByDateCreationDesc(String camionId);
    List<RouteOptimization> findAllByOrderByDateCreationDesc();
}
