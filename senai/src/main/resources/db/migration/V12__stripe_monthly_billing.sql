ALTER TABLE platform_account
    ADD COLUMN stripe_customer_id VARCHAR(255) NULL,
    ADD COLUMN stripe_subscription_id VARCHAR(255) NULL,
    ADD COLUMN stripe_subscription_status VARCHAR(30) NULL,
    ADD COLUMN stripe_checkout_session_id VARCHAR(255) NULL,
    ADD COLUMN stripe_checkout_expires_at DATETIME(6) NULL,
    ADD CONSTRAINT uk_platform_account_stripe_customer UNIQUE (stripe_customer_id),
    ADD CONSTRAINT uk_platform_account_stripe_subscription UNIQUE (stripe_subscription_id),
    ADD CONSTRAINT uk_platform_account_stripe_checkout UNIQUE (stripe_checkout_session_id);

CREATE TABLE stripe_webhook_event (
    id VARCHAR(255) PRIMARY KEY,
    type VARCHAR(100) NOT NULL,
    processed_at DATETIME(6) NOT NULL
);
