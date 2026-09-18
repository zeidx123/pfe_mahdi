package com.municipalite.gestiontournees;

import com.municipalite.gestiontournees.user.model.Role;
import com.municipalite.gestiontournees.user.model.User;
import com.municipalite.gestiontournees.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    /** Explicit first-run setup only. Existing accounts are never reset or reactivated. */
    @Bean
    @ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true")
    public CommandLineRunner dataInitializer(UserRepository users, PasswordEncoder encoder,
            @Value("${app.bootstrap.email:}") String email,
            @Value("${app.bootstrap.password:}") String password) {
        return args -> {
            if (email.isBlank() || password.length() < 10) {
                throw new IllegalArgumentException("Configurer app.bootstrap.email et un mot de passe d'au moins 10 caractères");
            }
            if (users.findByEmail(email).isEmpty()) {
                users.save(User.builder().email(email).password(encoder.encode(password))
                        .nom("Admin").prenom("Superviseur").role(Role.SUPERVISEUR)
                        .actif(true).createdAt(LocalDateTime.now()).build());
                log.info("Compte superviseur initial créé");
            }
        };
    }
}
