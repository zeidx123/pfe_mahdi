package com.municipalite.gestiontournees.tournee.repository;

import com.municipalite.gestiontournees.tournee.model.Tournee;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TourneeRepository extends MongoRepository<Tournee, String> {
    Optional<Tournee> findByReference(String reference);
}
