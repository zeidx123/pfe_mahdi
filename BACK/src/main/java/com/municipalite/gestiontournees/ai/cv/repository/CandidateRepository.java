package com.municipalite.gestiontournees.ai.cv.repository;

import com.municipalite.gestiontournees.ai.cv.model.Candidate;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CandidateRepository extends MongoRepository<Candidate, String> {
    List<Candidate> findByStatutOrderByDateDepotDesc(String statut);
    List<Candidate> findAllByOrderByDateDepotDesc();
    List<Candidate> findByRecommandationOrderByScoreCompatibiliteDesc(String recommandation);
}
