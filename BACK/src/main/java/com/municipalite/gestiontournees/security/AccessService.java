package com.municipalite.gestiontournees.security;

import com.municipalite.gestiontournees.tournee.model.Tournee;
import com.municipalite.gestiontournees.tournee.repository.TourneeRepository;
import com.municipalite.gestiontournees.user.model.Role;
import com.municipalite.gestiontournees.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/** Shared service-level checks for resources used by both HTTP and STOMP. */
@Service
@RequiredArgsConstructor
public class AccessService {
    private final TourneeRepository tourneeRepository;

    public User currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User user) || !user.isEnabled()) {
            throw new AuthenticationCredentialsNotFoundException("Authentification requise");
        }
        return user;
    }

    public boolean isSupervisor() {
        return currentUser().getRole() == Role.SUPERVISEUR;
    }

    public void requireSupervisor() {
        if (!isSupervisor()) throw new AccessDeniedException("Action réservée au superviseur");
    }

    public void requireTourAccess(Tournee tournee) {
        User user = currentUser();
        if (user.getRole() != Role.SUPERVISEUR
                && (user.getRole() != Role.CHAUFFEUR || user.getId() == null
                    || !Objects.equals(user.getId(), tournee.getChauffeurId()))) {
            throw new AccessDeniedException("Cette tournée ne vous est pas affectée");
        }
    }

    public Tournee requireTourAccess(String tourneeId) {
        if (tourneeId == null || tourneeId.isBlank()) throw new IllegalArgumentException("Tournée obligatoire");
        Tournee tournee = tourneeRepository.findById(tourneeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tournée introuvable"));
        requireTourAccess(tournee);
        return tournee;
    }
}
