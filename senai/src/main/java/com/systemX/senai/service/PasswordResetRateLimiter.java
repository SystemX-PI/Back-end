package com.systemX.senai.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetRateLimiter {
    private static final long WINDOW_SECONDS = 60;
    private final JdbcTemplate jdbc;
    private final SecretKeySpec secret;

    public PasswordResetRateLimiter(JdbcTemplate jdbc, @Value("${app.jwt.secret}") String secret) {
        this.jdbc = jdbc;
        this.secret = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public enum Decision { ACCEPT, ORIGIN_LIMIT, EMAIL_LIMIT, GLOBAL_LIMIT }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Decision request(String origin, String email) {
        if (!consume("request-origin", origin, 1)) return Decision.ORIGIN_LIMIT;
        if (!consume("request-global", "all", 5000)) return Decision.GLOBAL_LIMIT;
        return consume("request-email", email, 1) ? Decision.ACCEPT : Decision.EMAIL_LIMIT;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean reset(String origin) {
        return consume("reset-origin", origin, 30);
    }

    private boolean consume(String scope, String value, int max) {
        long now = Instant.now().getEpochSecond();
        String bucket = bucket(scope, value);
        // The upsert takes a row lock even for a brand-new bucket. The update stays in the same transaction.
        jdbc.update("""
                INSERT INTO password_reset_rate_limit (bucket, hits, expires_at) VALUES (?, 0, ?)
                ON DUPLICATE KEY UPDATE bucket = bucket
                """, bucket, now + WINDOW_SECONDS);
        return jdbc.update("""
                UPDATE password_reset_rate_limit
                SET hits = CASE WHEN expires_at <= ? THEN 1 ELSE hits + 1 END,
                    expires_at = CASE WHEN expires_at <= ? THEN ? ELSE expires_at END
                WHERE bucket = ? AND (expires_at <= ? OR hits < ?)
                """, now, now, now + WINDOW_SECONDS, bucket, now, max) == 1;
    }

    @Scheduled(fixedDelay = 3600000)
    public void limparExpirados() {
        jdbc.update("DELETE FROM password_reset_rate_limit WHERE expires_at < ?",
                Instant.now().minusSeconds(3600).getEpochSecond());
    }

    private String bucket(String scope, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secret);
            return HexFormat.of().formatHex(mac.doFinal(
                    ("password-reset-rate:" + scope + ":" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC indisponível", e);
        }
    }
}
