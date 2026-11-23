package com.systemX.senai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "password_reset_rate_limit",
        indexes = @Index(name = "ix_password_reset_rate_limit_expiry", columnList = "expires_at"))
public class PasswordResetRateLimit {
    @Id @Column(length = 64)
    private String bucket;
    @Column(nullable = false)
    private int hits;
    @Column(name = "expires_at", nullable = false)
    private long expiresAt;
}
