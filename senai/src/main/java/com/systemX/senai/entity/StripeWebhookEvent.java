package com.systemX.senai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "stripe_webhook_event")
public class StripeWebhookEvent {
    @Id
    @Column(length = 255)
    private String id;

    @Column(nullable = false, length = 100)
    private String type;

    @Column(nullable = false)
    private Instant processedAt = Instant.now();

    protected StripeWebhookEvent() {}

    public StripeWebhookEvent(String id, String type) {
        this.id = id;
        this.type = type;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public Instant getProcessedAt() { return processedAt; }
}
