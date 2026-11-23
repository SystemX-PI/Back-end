package com.systemX.senai.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonIgnore;
@Entity @Table(name="platform_account") @Getter @Setter
public class PlatformAccount {
 @JsonIgnore @Column(name = "internal_operations", unique = true)
 private Boolean internalOperations;
 @Column(name = "widget_origin", length = 255)
 private String widgetOrigin;
 @JsonIgnore @Column(name = "widget_key_digest", length = 64)
 private String widgetKeyDigest;
 @JsonIgnore @Column(name = "stripe_pix_session_id")
 private String stripePixSessionId;
 @JsonIgnore @Column(name = "stripe_pix_amount_cents")
 private Integer stripePixAmountCents;
 @JsonIgnore @Column(name = "stripe_pix_paid_until")
 private Instant stripePixPaidUntil;
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
 @JsonIgnore @Column(length=255, unique=true) private String stripeCustomerId;
 @JsonIgnore @Column(length=255, unique=true) private String stripeSubscriptionId;
 @Column(length=30) private String stripeSubscriptionStatus;
 @JsonIgnore @Column(length=255, unique=true) private String stripeCheckoutSessionId;
 @JsonIgnore private Instant stripeCheckoutExpiresAt;
 @JsonIgnore @Column(length=36) private String stripeCheckoutAttempt;
 @JsonIgnore @Column(length=10) private String stripeCheckoutAttemptMethod;
 @JsonIgnore private Instant stripeCheckoutAttemptAt;
 @JsonIgnore private Long stripeSubscriptionEventAt;
 @Column(nullable=false) private boolean professional = false;
 @Column(nullable=false) private boolean suspended = false;
}
