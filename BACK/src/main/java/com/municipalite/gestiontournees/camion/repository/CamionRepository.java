package com.municipalite.gestiontournees.camion.repository;

import com.municipalite.gestiontournees.camion.model.Camion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CamionRepository extends MongoRepository<Camion, String> {
    Optional<Camion> findByMatricule(String matricule);
}
