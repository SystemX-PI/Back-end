package com.systemX.senai.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Entity @Table(name="password_reset_token") @Getter @Setter
public class PasswordResetToken {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,unique=true) private Long usuarioId;
 @Column(nullable=false,length=64) private String tokenHash;
 @Column(nullable=false) private Instant expiresAt;
 @Column(nullable=false) private Instant requestedAt;
 @Column(nullable=false) private boolean used = false;
 @Column(nullable=false) private int attempts = 0;
 @Column(nullable=false) private int authVersion;
}
