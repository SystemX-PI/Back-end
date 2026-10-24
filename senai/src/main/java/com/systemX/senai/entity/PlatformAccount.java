package com.systemX.senai.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Entity @Table(name="platform_account") @Getter @Setter
public class PlatformAccount {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private String nome;
 private String ownerEmail;
 @Column(nullable=false) private String brandName;
 @Lob @Column(columnDefinition="LONGTEXT") private String logo;
 @Column(nullable=false) private String primaryColor = "#315fe8";
 @Column(nullable=false) private String accentColor = "#35cda0";
 @Column(nullable=false) private Instant createdAt = Instant.now();
 @Column(nullable=false) private Instant trialEndsAt = Instant.now().plusSeconds(259200);
 private Instant paidUntil;
 @Column(nullable=false) private boolean professional = false;
 @Column(nullable=false) private boolean suspended = false;
}
