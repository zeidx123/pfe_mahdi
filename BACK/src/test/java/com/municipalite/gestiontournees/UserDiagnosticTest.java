package com.municipalite.gestiontournees;

import com.municipalite.gestiontournees.user.model.User;
import com.municipalite.gestiontournees.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class UserDiagnosticTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    public void printUsers() {
        List<User> users = userRepository.findAll();
        System.out.println("=== USERS IN MONGO DB ===");
        for (User u : users) {
            System.out.println("User: id=" + u.getId() + ", email=" + u.getEmail() + ", role=" + u.getRole() + ", authorities=" + u.getAuthorities());
        }
    }
}
