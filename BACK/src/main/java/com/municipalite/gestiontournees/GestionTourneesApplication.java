package com.municipalite.gestiontournees;

import com.municipalite.gestiontournees.user.model.Role;
import com.municipalite.gestiontournees.user.model.User;
import com.municipalite.gestiontournees.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

@SpringBootApplication
@EnableAsync
@Slf4j
public class GestionTourneesApplication {

    public static void main(String[] args) {
        SpringApplication.run(GestionTourneesApplication.class, args);
    }

    @Bean
    public CommandLineRunner dataInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByEmail("admin@municipalite.ma").isEmpty()) {
                User admin = User.builder()
                        .email("admin@municipalite.ma")
                        .password(passwordEncoder.encode("admin123"))
                        .nom("Admin")
                        .prenom("Superviseur")
                        .role(Role.SUPERVISEUR)
                        .actif(true)
                        .createdAt(LocalDateTime.now())
                        .build();
                userRepository.save(admin);
                log.info("✅ Compte superviseur créé : admin@municipalite.ma / admin123");
            } else {
                log.info("ℹ️ Compte superviseur déjà existant.");
            }

            // Migration automatique : s'assurer que tous les chauffeurs ont leur CIN comme mot de passe
            long migrated = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.CHAUFFEUR && u.getCin() != null && !u.getCin().isEmpty())
                .peek(u -> {
                    u.setPassword(passwordEncoder.encode(u.getCin()));
                    u.setActif(true); // s'assurer que tous les agents sont actifs
                    userRepository.save(u);
                })
                .count();
            if (migrated > 0) {
                log.info("🔑 Migration: {} chauffeur(s) — mot de passe mis à jour au CIN.", migrated);
            }
        };
    }
}
