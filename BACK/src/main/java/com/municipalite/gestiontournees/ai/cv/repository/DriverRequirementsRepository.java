package com.municipalite.gestiontournees.ai.cv.repository;

import com.municipalite.gestiontournees.ai.cv.model.DriverRequirements;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface DriverRequirementsRepository extends MongoRepository<DriverRequirements, String> {
    Optional<DriverRequirements> findTopByOrderByVersionDesc();
}
