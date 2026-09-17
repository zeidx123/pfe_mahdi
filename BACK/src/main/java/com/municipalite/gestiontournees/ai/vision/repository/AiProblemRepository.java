package com.municipalite.gestiontournees.ai.vision.repository;

import com.municipalite.gestiontournees.ai.vision.model.AiProblem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiProblemRepository extends MongoRepository<AiProblem, String> {
    List<AiProblem> findByChauffeurIdOrderByCreatedAtDesc(String chauffeurId);
    List<AiProblem> findAllByOrderByCreatedAtDesc();
}
