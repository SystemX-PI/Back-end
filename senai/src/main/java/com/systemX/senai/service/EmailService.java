package com.systemX.senai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class EmailService {
    private final AtomicReference<EmailCredentials> credentials;

    public EmailService(
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${spring.mail.username:}") String from,
            @Value("${spring.mail.password:}") String password) {
        this.credentials = new AtomicReference<>(new EmailCredentials(enabled, from, password));
    }

    public boolean isEnabled() {
        EmailCredentials current = credentials.get();
        return current.enabled() && !current.from().isBlank() && !current.password().isBlank();
    }

    public void configure(String email, String appPassword) {
        credentials.set(new EmailCredentials(true, email.trim(), appPassword.replaceAll("\\s", "")));
    }

    public void send(String recipient, String subject, String text) {
        EmailCredentials current = credentials.get();
        if (!current.enabled() || current.from().isBlank() || current.password().isBlank()) {
            throw new IllegalStateException("E-mail nao configurado. Configure as credenciais de envio.");
        }

        Properties properties = new Properties();
        properties.put("mail.smtp.connectiontimeout", "3000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
        properties.put("mail.smtp.host", "smtp.gmail.com");
        properties.put("mail.smtp.port", "465");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.socketFactory.port", "465");
        properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        properties.put("mail.smtp.ssl.checkserveridentity", "true");

        Session session = Session.getInstance(properties, new jakarta.mail.Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(current.from(), current.password());
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(current.from()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
            message.setSubject(subject);
            message.setText(text);
            Transport.send(message);
        } catch (jakarta.mail.MessagingException exception) {
            String cause = exception.getMessage();
            if (exception.getCause() != null && exception.getCause().getMessage() != null) {
                cause = cause + " - " + exception.getCause().getMessage();
            }
            throw new IllegalStateException("Falha ao enviar e-mail pelo Gmail: " + cause, exception);
        }
    }

    private record EmailCredentials(boolean enabled, String from, String password) { }
}
