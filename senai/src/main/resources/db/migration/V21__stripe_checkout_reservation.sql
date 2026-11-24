ALTER TABLE platform_account
    ADD COLUMN stripe_checkout_attempt VARCHAR(36),
    ADD COLUMN stripe_checkout_attempt_method VARCHAR(10),
    ADD COLUMN stripe_checkout_attempt_at DATETIME(6),
    ADD COLUMN stripe_subscription_event_at BIGINT;
