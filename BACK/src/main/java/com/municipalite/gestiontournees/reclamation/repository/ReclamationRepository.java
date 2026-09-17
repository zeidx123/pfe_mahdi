package com.municipalite.gestiontournees.reclamation.repository;

import com.municipalite.gestiontournees.reclamation.model.Reclamation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReclamationRepository extends MongoRepository<Reclamation, String> {
    List<Reclamation> findByChauffeurId(String chauffeurId);
}
