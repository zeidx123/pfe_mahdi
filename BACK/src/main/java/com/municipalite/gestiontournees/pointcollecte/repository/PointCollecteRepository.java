package com.municipalite.gestiontournees.pointcollecte.repository;

import com.municipalite.gestiontournees.pointcollecte.model.PointCollecte;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PointCollecteRepository extends MongoRepository<PointCollecte, String> {
    Optional<PointCollecte> findByReference(String reference);
}
