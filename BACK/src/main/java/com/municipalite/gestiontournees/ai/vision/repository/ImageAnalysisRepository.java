package com.municipalite.gestiontournees.ai.vision.repository;

import com.municipalite.gestiontournees.ai.vision.model.ImageAnalysis;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImageAnalysisRepository extends MongoRepository<ImageAnalysis, String> {
    List<ImageAnalysis> findByTourneeIdOrderByCreatedAtDesc(String tourneeId);
    List<ImageAnalysis> findAllByOrderByCreatedAtDesc();
}
