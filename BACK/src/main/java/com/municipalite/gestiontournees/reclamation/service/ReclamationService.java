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
        Reclamation rec = reclamationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Réclamation introuvable"));

        rec.setReponse(reponse);
        rec.setStatut("TRAITEE");
        rec.setDateTraitement(LocalDateTime.now());

        return reclamationRepository.save(rec);
    }
}
