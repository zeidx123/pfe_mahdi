package com.municipalite.gestiontournees.agent.service;

import com.municipalite.gestiontournees.agent.dto.AgentRequest;
import com.municipalite.gestiontournees.agent.dto.AgentResponse;
import com.municipalite.gestiontournees.user.model.Role;
import com.municipalite.gestiontournees.user.model.User;
import com.municipalite.gestiontournees.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public List<AgentResponse> getAllAgents() {
        return userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.CHAUFFEUR)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public AgentResponse getAgentById(String id) {
        User user = userRepository.findById(id)
                .filter(u -> u.getRole() == Role.CHAUFFEUR)
                .orElseThrow(() -> new RuntimeException("Agent introuvable"));
        return mapToResponse(user);
    }

    public AgentResponse createAgent(AgentRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("L'email personnel est déjà utilisé");
        }

        // Génération automatique du matricule (ex: 222LMT100)
        String newMatricule = "222LMT100";
        var lastUser = userRepository.findTopByRoleOrderByMatriculeDesc(Role.CHAUFFEUR);
        if (lastUser.isPresent() && lastUser.get().getMatricule() != null && lastUser.get().getMatricule().startsWith("222LMT")) {
            try {
                int lastNumber = Integer.parseInt(lastUser.get().getMatricule().substring(6));
                newMatricule = "222LMT" + (lastNumber + 1);
            } catch (NumberFormatException ignored) {}
        }

        // Le mot de passe par défaut est le CIN
        String defaultPassword = request.getCin();

        User user = User.builder()
                .matricule(newMatricule)
                .nom(request.getNom())
                .prenom(request.getPrenom())
                .cin(request.getCin())
                .email(request.getEmail())
                .password(passwordEncoder.encode(defaultPassword))
                .telephone(request.getTelephone())
                .adresse(request.getAdresse())
                .numeroPermis(request.getNumeroPermis())
                .dateExpirationPermis(request.getDateExpirationPermis())
                .typePermis(request.getTypePermis())
                .dateEmbauche(request.getDateEmbauche())
                .photo(request.getPhoto())
                .role(Role.CHAUFFEUR)
                .actif(request.isActif())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(user);

        // Envoyer l'email de bienvenue
        emailService.sendWelcomeEmail(savedUser.getEmail(), savedUser.getNom(), savedUser.getPrenom(), savedUser.getMatricule(), defaultPassword);

        return mapToResponse(savedUser);
    }

    public AgentResponse updateAgent(String id, AgentRequest request) {
        User user = userRepository.findById(id)
                .filter(u -> u.getRole() == Role.CHAUFFEUR)
                .orElseThrow(() -> new RuntimeException("Agent introuvable"));

        if (!user.getEmail().equals(request.getEmail()) && 
            userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("L'email est déjà utilisé par un autre utilisateur");
        }

        user.setNom(request.getNom());
        user.setPrenom(request.getPrenom());
        user.setCin(request.getCin());
        user.setEmail(request.getEmail());
        user.setTelephone(request.getTelephone());
        user.setAdresse(request.getAdresse());
        user.setNumeroPermis(request.getNumeroPermis());
        user.setDateExpirationPermis(request.getDateExpirationPermis());
        user.setTypePermis(request.getTypePermis());
        user.setDateEmbauche(request.getDateEmbauche());
        user.setPhoto(request.getPhoto());
        user.setActif(request.isActif());
        user.setUpdatedAt(LocalDateTime.now());

        return mapToResponse(userRepository.save(user));
    }

    public void deleteAgent(String id) {
        User user = userRepository.findById(id)
                .filter(u -> u.getRole() == Role.CHAUFFEUR)
                .orElseThrow(() -> new RuntimeException("Agent introuvable"));
        
        // Suppression logique
        user.setActif(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public void deleteAgentPermanently(String id) {
        User user = userRepository.findById(id)
                .filter(u -> u.getRole() == Role.CHAUFFEUR)
                .orElseThrow(() -> new RuntimeException("Agent introuvable"));
        
        // Suppression définitive de la base de données
        userRepository.deleteById(user.getId());
    }

    public void resetPasswordToCin(String id) {
        User user = userRepository.findById(id)
                .filter(u -> u.getRole() == Role.CHAUFFEUR)
                .orElseThrow(() -> new RuntimeException("Agent introuvable"));
        user.setPassword(passwordEncoder.encode(user.getCin()));
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userRepository.save(user);
    }

    private AgentResponse mapToResponse(User user) {
        return AgentResponse.builder()
                .id(user.getId())
                .matricule(user.getMatricule())
                .nom(user.getNom())
                .prenom(user.getPrenom())
                .cin(user.getCin())
                .email(user.getEmail())
                .telephone(user.getTelephone())
                .adresse(user.getAdresse())
                .numeroPermis(user.getNumeroPermis())
                .dateExpirationPermis(user.getDateExpirationPermis())
                .typePermis(user.getTypePermis())
                .dateEmbauche(user.getDateEmbauche())
                .photo(user.getPhoto())
                .role(user.getRole())
                .actif(user.isActif())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
