package com.municipalite.gestiontournees.user.service;

import com.municipalite.gestiontournees.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        // L'identifiant peut être l'email, le matricule ou le numéro de CIN
        return userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByMatricule(identifier))
                .or(() -> userRepository.findByCin(identifier))
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Utilisateur introuvable avec l'identifiant : " + identifier));
    }
}
