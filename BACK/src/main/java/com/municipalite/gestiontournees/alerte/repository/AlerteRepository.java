package com.municipalite.gestiontournees.alerte.repository;

import com.municipalite.gestiontournees.alerte.model.Alerte;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlerteRepository extends MongoRepository<Alerte, String> {
    long countByResolueFalse();
}
