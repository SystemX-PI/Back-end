package com.systemX.senai.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.systemX.senai.exception.InvalidCredentialsException;

/** Verifies Google ID tokens and returns only the identity claims used by this application. */
@Service
public class GoogleIdentityService {
    private final String clientId;
    private final GoogleIdTokenVerifier verifier;

    public GoogleIdentityService(@Value("${app.auth.google.client-id:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.verifier = this.clientId.isBlank() ? null
                : new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), JacksonFactory.getDefaultInstance())
                        .setAudience(List.of(this.clientId))
                        .build();
    }

    public Identity verify(String credential) {
        if (verifier == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O login com Google ainda não foi configurado no servidor.");
        }
        try {
            GoogleIdToken token = verifier.verify(credential);
            if (token == null) {
                throw new InvalidCredentialsException();
            }
            GoogleIdToken.Payload payload = token.getPayload();
            String subject = payload.getSubject();
            String email = payload.getEmail();
            if (subject == null || subject.isBlank() || subject.length() > 255
                    || email == null || email.isBlank() || email.length() > 255 || !email.contains("@")
                    || !Boolean.TRUE.equals(payload.getEmailVerified())) {
                throw new InvalidCredentialsException();
            }

            String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
            Object nameClaim = payload.get("name");
            String name = nameClaim instanceof String value && !value.isBlank()
                    ? value.trim()
                    : normalizedEmail.substring(0, normalizedEmail.indexOf('@'));
            Object pictureClaim = payload.get("picture");
            String picture = pictureClaim instanceof String value
                    && value.startsWith("https://") && value.length() <= 2048
                    ? value
                    : null;
            return new Identity(subject, normalizedEmail, name, picture);
        } catch (GeneralSecurityException | IOException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível validar a conta Google neste momento.");
        }
    }

    public record Identity(String subject, String email, String name, String picture) { }
}
