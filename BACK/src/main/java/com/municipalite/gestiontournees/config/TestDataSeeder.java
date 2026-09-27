package com.municipalite.gestiontournees.config;

import com.municipalite.gestiontournees.user.model.Role;
import com.municipalite.gestiontournees.user.model.User;
import com.municipalite.gestiontournees.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Local/test convenience data only. Disabled by default; never enable in production.
 * Existing accounts (matched by email) are left untouched, so this is safe to run repeatedly.
 */
@Configuration
@Slf4j
public class TestDataSeeder {

    @Bean
    @ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
    public CommandLineRunner seedTestUsers(UserRepository users, PasswordEncoder encoder) {
        return args -> {
            createIfAbsent(users, encoder, User.builder()
                    .email("superviseur.test@gesticollecte.local")
                    .password("Superviseur123!")
                    .nom("Ben Salah")
                    .prenom("Amine")
                    .role(Role.SUPERVISEUR)
                    .actif(true)
                    .createdAt(LocalDateTime.now())
                    .build());

            createIfAbsent(users, encoder, User.builder()
                    .email("chauffeur1.test@gesticollecte.local")
                    .matricule("222LMT900")
                    .cin("09012345")
                    .password("09012345")
                    .nom("Trabelsi")
                    .prenom("Karim")
                    .telephone("20123456")
                    .numeroPermis("PC900123")
                    .typePermis("C")
                    .dateExpirationPermis(LocalDate.now().plusYears(3))
                    .dateEmbauche(LocalDate.now().minusYears(1))
                    .role(Role.CHAUFFEUR)
                    .actif(true)
                    .createdAt(LocalDateTime.now())
                    .build());

            createIfAbsent(users, encoder, User.builder()
                    .email("chauffeur2.test@gesticollecte.local")
                    .matricule("222LMT901")
                    .cin("09054321")
                    .password("09054321")
                    .nom("Gharbi")
                    .prenom("Sana")
                    .telephone("20654321")
                    .numeroPermis("PC900456")
                    .typePermis("C")
                    .dateExpirationPermis(LocalDate.now().plusYears(2))
                    .dateEmbauche(LocalDate.now().minusMonths(6))
                    .role(Role.CHAUFFEUR)
                    .actif(true)
                    .createdAt(LocalDateTime.now())
                    .build());
        };
    }

    private void createIfAbsent(UserRepository users, PasswordEncoder encoder, User template) {
        if (users.findByEmail(template.getEmail()).isPresent()) {
            return;
        }
        template.setPassword(encoder.encode(template.getPassword()));
        users.save(template);
        log.info("Compte de test créé : {}", template.getEmail());
    }
}
