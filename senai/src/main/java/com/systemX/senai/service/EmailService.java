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

@Service
public class EmailService {
    private final boolean enabled;
    private final String from;
    private final String password;

    public EmailService(
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${spring.mail.username:}") String from,
            @Value("${spring.mail.password:}") String password) {
        this.enabled = enabled;
        this.from = from;
        this.password = password;
    }

    public boolean isEnabled() { return enabled; }

    public void send(String recipient, String subject, String text) {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", "smtp.gmail.com");
        properties.put("mail.smtp.port", "465");
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.socketFactory.port", "465");
        properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

        Session session = Session.getInstance(properties, new jakarta.mail.Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(from, password);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
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
}