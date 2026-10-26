package com.municipalite.gestiontournees.user.repository;

import com.municipalite.gestiontournees.user.model.Role;
import com.municipalite.gestiontournees.user.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByMatricule(String matricule);
    Optional<User> findByCin(String cin);
    Optional<User> findTopByRoleOrderByMatriculeDesc(Role role);
}
