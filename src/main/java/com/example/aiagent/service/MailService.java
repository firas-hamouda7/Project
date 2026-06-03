package com.example.aiagent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.aiagent.entity.User;
import com.example.aiagent.entity.UserStatus;

import jakarta.mail.internet.MimeMessage;

@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String frontendUrl;

    public MailService(JavaMailSender mailSender,
                       @Value("${spring.mail.from:no-reply@ghost-employer.local}") String fromAddress,
                       @Value("${application.frontend-url:http://localhost:4200}") String frontendUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.frontendUrl = frontendUrl;
    }

    @Async
    public void sendResetPasswordEmail(String to, String token) {
        String resetUrl = frontendUrl + "/reset-password?token=" + token;
        System.out.println("Tentative d'envoi d'email à : " + to);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject("Réinitialisation du mot de passe Ghost Employer");
            helper.setText(
                "<p>Bonjour,</p>" +
                "<p>Vous avez demandé à réinitialiser votre mot de passe. Cliquez sur le lien ci-dessous :</p>" +
                "<p><a href=\"" + resetUrl + "\">Réinitialiser mon mot de passe</a></p>" +
                "<p>Ce lien expire dans 1 heure.</p>" +
                "<p>Si vous n'avez pas demandé cette action, ignorez cet email.</p>",
                true
            );
            mailSender.send(message);
            System.out.println("Email envoyé avec succès à : " + to);
        } catch (Exception ex) {
            System.err.println("Erreur lors de l'envoi de l'email : " + ex.getMessage());
            throw new RuntimeException("Impossible d'envoyer l'email de réinitialisation", ex);
        }
    }
    
    @Async
    public void sendStatusChangeEmail(User user, UserStatus status) {
        String subject = "";
        String content = "";
        
        switch (status) {
            case ACTIF:
                subject = "Votre compte a été activé";
                content = "<p>Bonjour " + user.getNom() + ",</p>" +
                          "<p>Votre compte Ghost Employer a été activé avec succès.</p>" +
                          "<p>Vous pouvez désormais accéder à la plateforme.</p>" +
                          "<p>Cordialement,<br>L'équipe Ghost Employer.</p>";
                break;
            case SUSPENDU:
                subject = "Votre compte a été suspendu";
                content = "<p>Bonjour " + user.getNom() + ",</p>" +
                          "<p>Votre compte Ghost Employer a été temporairement suspendu.</p>" +
                          "<p>Si vous pensez qu'il s'agit d'une erreur, veuillez contacter l'administration.</p>" +
                          "<p>Cordialement,<br>L'équipe Ghost Employer.</p>";
                break;
            case DESACTIVE:
                subject = "Votre compte a été désactivé";
                content = "<p>Bonjour " + user.getNom() + ",</p>" +
                          "<p>Votre compte Ghost Employer a été désactivé.</p>" +
                          "<p>Vous n'avez plus accès à la plateforme.</p>" +
                          "<p>Pour plus d'informations, contactez l'administration.</p>" +
                          "<p>Cordialement,<br>L'équipe Ghost Employer.</p>";
                break;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(content, true);
            mailSender.send(message);
            System.out.println("Email de changement de statut (" + status + ") envoyé à : " + user.getEmail());
        } catch (Exception ex) {
            System.err.println("Erreur lors de l'envoi de l'email de statut : " + ex.getMessage());
        }
    }
}
