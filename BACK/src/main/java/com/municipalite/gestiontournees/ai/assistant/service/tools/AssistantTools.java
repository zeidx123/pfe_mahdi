package com.municipalite.gestiontournees.ai.assistant.service.tools;

import com.municipalite.gestiontournees.ai.cv.model.Candidate;
import com.municipalite.gestiontournees.ai.cv.repository.CandidateRepository;
import com.municipalite.gestiontournees.ai.vision.repository.AiProblemRepository;
import com.municipalite.gestiontournees.camion.model.Camion;
import com.municipalite.gestiontournees.camion.model.EtatCamion;
import com.municipalite.gestiontournees.camion.model.StatutCamion;
import com.municipalite.gestiontournees.camion.repository.CamionRepository;
import com.municipalite.gestiontournees.pointcollecte.repository.PointCollecteRepository;
import com.municipalite.gestiontournees.reclamation.model.Reclamation;
import com.municipalite.gestiontournees.reclamation.repository.ReclamationRepository;
import com.municipalite.gestiontournees.tournee.model.Tournee;
import com.municipalite.gestiontournees.tournee.repository.TourneeRepository;
import com.municipalite.gestiontournees.user.model.Role;
import com.municipalite.gestiontournees.user.model.User;
import com.municipalite.gestiontournees.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service fournissant les données et outils de lecture en temps réel du site pour l'Assistant IA.
 */
@Service
@RequiredArgsConstructor
public class AssistantTools {

    private final UserRepository userRepository;
    private final CamionRepository camionRepository;
    private final TourneeRepository tourneeRepository;
    private final PointCollecteRepository pointCollecteRepository;
    private final AiProblemRepository aiProblemRepository;
    private final ReclamationRepository reclamationRepository;
    private final CandidateRepository candidateRepository;

    public Map<String, Object> getGlobalStatistics() {
        Map<String, Object> stats = new HashMap<>();
        List<User> chauffeurs = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.CHAUFFEUR)
                .collect(Collectors.toList());

        List<Camion> camions = camionRepository.findAll();
        long camionsEnPanne = camions.stream()
                .filter(c -> c.getStatut() == StatutCamion.EN_MAINTENANCE || c.getEtat() == EtatCamion.CRITIQUE)
                .count();

        long camionsEnTournee = camions.stream()
                .filter(c -> c.getStatut() == StatutCamion.EN_TOURNEE)
                .count();

        long camionsDisponibles = camions.stream()
                .filter(c -> c.getStatut() == StatutCamion.DISPONIBLE)
                .count();

        List<Reclamation> pannesMecaniques = reclamationRepository.findAll().stream()
                .filter(r -> "PANNE_MECANIQUE".equalsIgnoreCase(r.getType()))
                .collect(Collectors.toList());

        stats.put("totalAgents", chauffeurs.size());
        stats.put("totalCamions", camions.size());
        stats.put("camionsEnPanne", camionsEnPanne);
        stats.put("camionsEnTournee", camionsEnTournee);
        stats.put("camionsDisponibles", camionsDisponibles);
        stats.put("totalReclamationsPanne", pannesMecaniques.size());
        stats.put("totalTournees", tourneeRepository.count());
        stats.put("totalPointsCollecte", pointCollecteRepository.count());
        stats.put("totalProblemesIa", aiProblemRepository.count());
        stats.put("totalReclamations", reclamationRepository.count());
        stats.put("totalCandidatures", candidateRepository.count());
        return stats;
    }

    public String buildSystemContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== BASE DE DONNÉES EN TEMPS RÉEL ET DÉTAILS DU SITE GESTICOLLECTE ===\n\n");

        // 1. STATISTIQUES GLOBALES
        Map<String, Object> stats = getGlobalStatistics();
        sb.append("📊 SYNTHÈSE GLOBALE DU SYSTEME :\n");
        sb.append("- Total Chauffeurs/Agents enregistrés : ").append(stats.get("totalAgents")).append("\n");
        sb.append("- Total Camions dans la flotte : ").append(stats.get("totalCamions")).append("\n");
        sb.append("  ↳ Camions disponibles : ").append(stats.get("camionsDisponibles")).append("\n");
        sb.append("  ↳ Camions en tournée : ").append(stats.get("camionsEnTournee")).append("\n");
        sb.append("  ↳ Camions en panne / maintenance : ").append(stats.get("camionsEnPanne")).append("\n");
        sb.append("- Total Réclamations pannes mécaniques : ").append(stats.get("totalReclamationsPanne")).append("\n");
        sb.append("- Total Tournées créées : ").append(stats.get("totalTournees")).append("\n");
        sb.append("- Total Points de collecte : ").append(stats.get("totalPointsCollecte")).append("\n");
        sb.append("- Total Problèmes détectés par Vision IA : ").append(stats.get("totalProblemesIa")).append("\n");
        sb.append("- Total Candidatures CVs IA reçues : ").append(stats.get("totalCandidatures")).append("\n\n");

        // 2. DÉTAILS DE LA FLOTTE DE CAMIONS
        List<Camion> camions = camionRepository.findAll();
        sb.append("🚚 DÉTAILS DE LA FLOTTE DE CAMIONS (").append(camions.size()).append(" véhicules) :\n");
        if (camions.isEmpty()) {
            sb.append("  (Aucun camion enregistré dans la base)\n");
        } else {
            for (Camion c : camions) {
                sb.append("  • Camion Mat: ").append(c.getMatricule())
                  .append(" | Marque: ").append(c.getMarque() != null ? c.getMarque() : "N/C")
                  .append(" | Modèle: ").append(c.getModele() != null ? c.getModele() : "N/C")
                  .append(" | Statut: ").append(c.getStatut() != null ? c.getStatut() : "DISPONIBLE")
                  .append(" | État: ").append(c.getEtat() != null ? c.getEtat() : "BON")
                  .append(" | Capacité: ").append(c.getCapacitePoids() != null ? c.getCapacitePoids() + " kg" : "N/C")
                  .append("\n");
            }
        }
        sb.append("\n");

        // 3. DÉTAILS DES CHAUFFEURS / AGENTS
        List<User> agents = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.CHAUFFEUR)
                .collect(Collectors.toList());
        sb.append("👤 DÉTAILS DES AGENTS / CHAUFFEURS MUNICIPAUX (").append(agents.size()).append(" agents) :\n");
        if (agents.isEmpty()) {
            sb.append("  (Aucun agent enregistré dans la base)\n");
        } else {
            for (User u : agents) {
                sb.append("  • Agent: ").append(u.getPrenom() != null ? u.getPrenom() : "").append(" ").append(u.getNom() != null ? u.getNom() : "")
                  .append(" | Matricule: ").append(u.getMatricule() != null ? u.getMatricule() : "N/C")
                  .append(" | CIN: ").append(u.getCin() != null ? u.getCin() : "N/C")
                  .append(" | Permis: ").append(u.getTypePermis() != null ? u.getTypePermis() : "N/C")
                  .append(" | Tél: ").append(u.getTelephone() != null ? u.getTelephone() : "N/C")
                  .append(" | Statut: ").append(u.isActif() ? "ACTIF" : "INACTIF")
                  .append("\n");
            }
        }
        sb.append("\n");

        // 4. DÉTAILS DES TOURNÉES
        List<Tournee> tournees = tourneeRepository.findAll();
        sb.append("🚛 DÉTAILS DES TOURNÉES (").append(tournees.size()).append(" tournées) :\n");
        if (tournees.isEmpty()) {
            sb.append("  (Aucune tournée enregistrée)\n");
        } else {
            int count = 0;
            for (Tournee t : tournees) {
                if (count++ >= 10) break;
                sb.append("  • Réf: ").append(t.getReference() != null ? t.getReference() : t.getId())
                  .append(" | Nom: ").append(t.getNom() != null ? t.getNom() : "Tournée")
                  .append(" | Statut: ").append(t.getStatut() != null ? t.getStatut() : "A_FAIRE")
                  .append(" | Date: ").append(t.getDatePrevue() != null ? t.getDatePrevue() : "N/C")
                  .append("\n");
            }
        }
        sb.append("\n");

        // 5. DÉTAILS DES CANDIDATURES IA REÇUES
        List<Candidate> candidates = candidateRepository.findAllByOrderByDateDepotDesc();
        sb.append("📄 DÉTAILS DES CANDIDATURES CV (").append(candidates.size()).append(" reçues) :\n");
        if (candidates.isEmpty()) {
            sb.append("  (Aucune candidature reçue)\n");
        } else {
            for (Candidate c : candidates) {
                sb.append("  • ").append(c.getPrenom() != null ? c.getPrenom() : "").append(" ").append(c.getNom() != null ? c.getNom() : c.getCvFileName())
                  .append(" | CIN: ").append(c.getCin() != null ? c.getCin() : "Non renseignée")
                  .append(" | Permis: ").append(c.getTypePermis() != null ? c.getTypePermis() : "N/C")
                  .append(" | Score: ").append(c.getScoreCompatibilite() != null ? c.getScoreCompatibilite() + "%" : "0%")
                  .append(" | Recommandation: ").append(c.getRecommandation() != null ? c.getRecommandation() : "N/C")
                  .append(" | Statut: ").append(c.getStatut() != null ? c.getStatut() : "EN_ATTENTE")
                  .append("\n");
            }
        }
        sb.append("\n");

        // 6. PROCÉDURES OPÉRATIONNELLES ET CONSIGNES
        sb.append("📋 PROCÉDURES OPERATIONNELLES ET CONSIGNES MUNICIPALES :\n");
        sb.append("1. Panne de camion en tournée : L'agent doit immédiatement garer le véhicule en sécurité, allumer les feux de détresse, poser le triangle et envoyer un signalement via l'application mobile. Le superviseur déclenche l'intervention de la dépanneuse municipale.\n");
        sb.append("2. Problème de pneu / Crevaison : Ne jamais rouler à plat. Signaler l'incident et attendre l'équipe de maintenance.\n");
        sb.append("3. Conteneur débordant / Anomalie collecte : Prendre une photo en direct via l'application mobile Flutter. L'IA de vision analyse le type de déchet et l'état du bac.\n");
        sb.append("4. Critères de recrutement Chauffeur (Tunisie) : Âge obligatoire entre 20 et 35 ans, CIN tunisienne valide (8 chiffres), Permis B ou C minimum, et au moins 2 ans d'expérience.\n");

        return sb.toString();
    }
}
