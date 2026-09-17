package com.municipalite.gestiontournees.agent.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Async
    public void sendWelcomeEmail(String toEmail, String nom, String prenom, String matricule, String password) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("mahdifatnassi155@gmail.com", "GestiCollecte - Supervision");
            helper.setTo(toEmail);
            helper.setSubject("Bienvenue sur GestiCollecte - Vos identifiants");

            String htmlContent = """
                    <html>
                    <body style="font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: 0 auto;">
                        <div style="background-color: #0369a1; padding: 20px; text-align: center; border-radius: 8px 8px 0 0;">
                            <h1 style="color: white; margin: 0;">GestiCollecte</h1>
                        </div>
                        <div style="padding: 20px; border: 1px solid #ddd; border-top: none; border-radius: 0 0 8px 8px;">
                            <p>Bonjour <strong>%s %s</strong>,</p>
                            <p>Bienvenue dans l'équipe ! Votre compte chauffeur a été créé avec succès.</p>
                            <p>Veuillez trouver ci-dessous vos identifiants pour vous connecter à l'application mobile :</p>
                            <div style="background-color: #f1f5f9; padding: 15px; border-radius: 8px; margin: 20px 0;">
                                <p style="margin: 5px 0;"><strong>Identifiant :</strong> %s</p>
                                <p style="margin: 5px 0;"><strong>Mot de passe :</strong> %s <em>(Votre CIN)</em></p>
                            </div>
                            <p>Nous vous recommandons de changer ce mot de passe dès votre première connexion.</p>
                            <br/>
                            <p>Cordialement,</p>
                            <p><strong>L'équipe Supervision GestiCollecte</strong></p>
                        </div>
                    </body>
                    </html>
                    """.formatted(prenom, nom, matricule, password);

            helper.setText(htmlContent, true);
            mailSender.send(message);

        } catch (MessagingException | UnsupportedEncodingException e) {
            System.err.println("❌ Erreur lors de l'envoi de l'email à " + toEmail + " : " + e.getMessage());
        }
    }
}
