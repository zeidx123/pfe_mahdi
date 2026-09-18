package com.municipalite.gestiontournees.ai.cv.service;

import com.municipalite.gestiontournees.ai.cv.model.DriverRequirements;
import com.municipalite.gestiontournees.ai.cv.repository.DriverRequirementsRepository;
import com.municipalite.gestiontournees.security.AccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class DriverRequirementsService {
    private final DriverRequirementsRepository repository;
    private final AccessService access;
    private static final Set<String> LICENCES = Set.of("A", "A1", "A2", "B", "BE", "C", "C1", "CE", "C1E", "D", "D1", "DE", "D1E");

    public DriverRequirements current() {
        return repository.findTopByOrderByVersionDesc().orElseGet(DriverRequirements::initial);
    }

    public DriverRequirements update(DriverRequirements requirements) {
        access.requireSupervisor();
        validate(requirements);
        int version = current().getVersion() + 1;
        requirements.setId(UUID.randomUUID().toString());
        requirements.setVersion(version);
        requirements.setEditedBy(access.currentUser().getId());
        requirements.setEditedAt(LocalDateTime.now());
        return repository.save(requirements);
    }

    public void validate(DriverRequirements r) {
        if (r.getLabel() == null || r.getLabel().isBlank() || r.getLabel().length() > 160) {
            throw new IllegalArgumentException("Le libellé du profil est obligatoire (160 caractères maximum)");
        }
        List<Integer> weights = List.of(r.getLicenceWeight(), r.getExperienceWeight(), r.getSafetyWeight(), r.getCollectionWeight());
        if (weights.stream().anyMatch(w -> w < 0 || w > 100) || weights.stream().mapToInt(Integer::intValue).sum() != 100) {
            throw new IllegalArgumentException("Les poids doivent être positifs ou nuls et totaliser 100");
        }
        if (!Double.isFinite(r.getExperienceTargetYears()) || r.getExperienceTargetYears() <= 0 || r.getExperienceTargetYears() > 60) {
            throw new IllegalArgumentException("La cible d'expérience doit être comprise entre 0 (exclu) et 60 ans");
        }
        if (r.getAcceptedLicences() == null || r.getAcceptedLicences().isEmpty()
                || r.getAcceptedLicences().stream().anyMatch(p -> p == null || !LICENCES.contains(p.trim().toUpperCase(Locale.ROOT)))) {
            throw new IllegalArgumentException("Indiquer les catégories de permis acceptées");
        }
        r.setAcceptedLicences(r.getAcceptedLicences().stream().map(p -> p.trim().toUpperCase(Locale.ROOT)).distinct().toList());
    }
}
