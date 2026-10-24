package com.systemX.senai.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Entity @Table(name="platform_checkout") @Getter @Setter
public class PlatformCheckout {
 @Id @Column(length=36) private String id;
 @Column(nullable=false) private Long contaId;
 @Column(nullable=false,length=10) private String paymentMethod;
 @Column(nullable=false,length=20) private String status = "PENDING";
 @Column(nullable=false) private int amountCents;
 @Column(nullable=false) private Instant createdAt = Instant.now();
 @Column(nullable=false) private Instant expiresAt = Instant.now().plusSeconds(1800);
 private Instant paidAt;
}
