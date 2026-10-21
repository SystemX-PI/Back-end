package com.systemX.senai.service;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.mail.MessagingException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.systemX.senai.dto.EmailConfigurationRequest;
import com.systemX.senai.dto.EmailConfigurationResponse;

@Service
public class EmailConfigurationService {
    private final AtomicReference<EmailSettings> settings = new AtomicReference<>();

    public EmailConfigurationResponse configure(EmailConfigurationRequest request) {
        String password = request.senhaAplicativo().replaceAll("\\s", "");
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("smtp.gmail.com");
        sender.setPort(587);
        sender.setUsername(request.email().trim());
        sender.setPassword(password);

        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");

        try {
            sender.testConnection();
        } catch (MailException | MessagingException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Não foi possível autenticar no Gmail. Confira o endereço e a senha de aplicativo.");
        }

        settings.set(new EmailSettings(request.email().trim(), sender));
        return new EmailConfigurationResponse(true, request.email().trim());
    }

    private record EmailSettings(String email, JavaMailSenderImpl sender) {}
}
