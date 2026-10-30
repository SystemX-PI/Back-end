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
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Verifies Google ID tokens and returns only the identity claims used by this application. */
@Service
public class GoogleIdentityService {
    private static final Logger log = LoggerFactory.getLogger(GoogleIdentityService.class);
    private final String clientId;
    private final GoogleIdTokenVerifier verifier;

    public GoogleIdentityService(@Value("${app.auth.google.client-id:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.verifier = this.clientId.isBlank() ? null
                : new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(List.of(this.clientId))
                        .build();
    }

    public Identity verify(String credential) {
        if (verifier == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O login com Google ainda não foi configurado no servidor.");
        }
        GoogleIdToken token;
        try {
            token = GoogleIdToken.parse(GsonFactory.getDefaultInstance(), credential);
        } catch (IOException | IllegalArgumentException exception) {
            log.warn("Google sign-in rejected: malformed_id_token");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "O Google retornou uma autenticação inválida. Atualize a página e entre com Google novamente.");
        }
        try {
            if (!verifier.verify(token)) {
                // Record only validation outcomes, never tokens, claims, email or subject.
                log.warn("Google sign-in rejected: audience_valid={}, issuer_valid={}, time_valid={}",
                        token.verifyAudience(List.of(clientId)),
                        token.verifyIssuer(List.of("accounts.google.com", "https://accounts.google.com")),
                        token.verifyTime(System.currentTimeMillis(), verifier.getAcceptableTimeSkewSeconds()));
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Não foi possível validar a autenticação Google. Atualize a página e selecione sua conta novamente.");
            }
            GoogleIdToken.Payload payload = token.getPayload();
            String subject = payload.getSubject();
            String email = payload.getEmail();
            if (subject == null || subject.isBlank() || subject.length() > 255
                    || email == null || email.isBlank() || email.length() > 255 || !email.contains("@")
                    || !Boolean.TRUE.equals(payload.getEmailVerified())) {
                log.warn("Google sign-in rejected: missing_or_unverified_identity");
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "A conta Google precisa ter um e-mail verificado para entrar.");
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
            log.warn("Google sign-in unavailable: {}", exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível validar a conta Google neste momento.");
        }
    }

    public record Identity(String subject, String email, String name, String picture) { }
}
