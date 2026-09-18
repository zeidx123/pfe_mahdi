package com.municipalite.gestiontournees.reclamation.service;

import com.municipalite.gestiontournees.reclamation.model.Reclamation;
import com.municipalite.gestiontournees.reclamation.repository.ReclamationRepository;
import com.municipalite.gestiontournees.user.model.User;
import com.municipalite.gestiontournees.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReclamationService {

    private final ReclamationRepository reclamationRepository;
    private final UserRepository userRepository;

    public Reclamation createReclamation(Reclamation reclamation, String userIdentifier) {
        if (reclamation.getTitre() == null || reclamation.getTitre().isBlank()
                || reclamation.getDescription() == null || reclamation.getDescription().isBlank()
                || reclamation.getType() == null
                || !java.util.Set.of("PANNE_MECANIQUE", "ROUTE_BLOQUEE", "BAC_DEGRADE", "AUTRE").contains(reclamation.getType())) {
            throw new IllegalArgumentException("Titre, type et description valides sont obligatoires");
        }
        // The create endpoint must never overwrite another complaint by accepting an id.
        reclamation.setId(null);
        reclamation.setTitre(reclamation.getTitre().trim());
        reclamation.setDescription(reclamation.getDescription().trim());
        User user = userRepository.findByEmail(userIdentifier)
                .or(() -> userRepository.findByMatricule(userIdentifier))
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        reclamation.setChauffeurId(user.getId());
        reclamation.setChauffeurNom(user.getPrenom() + " " + user.getNom());
        reclamation.setStatut("EN_ATTENTE");
        reclamation.setDateCreation(LocalDateTime.now());
        reclamation.setReponse(null);
        reclamation.setDateTraitement(null);

        return reclamationRepository.save(reclamation);
    }

    public List<Reclamation> getReclamationsByChauffeur(String userIdentifier) {
        User user = userRepository.findByEmail(userIdentifier)
                .or(() -> userRepository.findByMatricule(userIdentifier))
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        return reclamationRepository.findByChauffeurId(user.getId());
    }

    public List<Reclamation> getAllReclamations() {
        return reclamationRepository.findAll();
    }

    public Reclamation repondreReclamation(String id, String reponse) {
        if (reponse == null || reponse.isBlank() || reponse.length() > 5000) {
            throw new IllegalArgumentException("Une réponse de 1 à 5000 caractères est obligatoire");
        }
        Reclamation rec = reclamationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Réclamation introuvable"));

        rec.setReponse(reponse);
        rec.setStatut("TRAITEE");
        rec.setDateTraitement(LocalDateTime.now());

        return reclamationRepository.save(rec);
    }
}
