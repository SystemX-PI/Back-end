ALTER TABLE platform_account
    ADD COLUMN widget_origin VARCHAR(255),
    ADD COLUMN widget_key_digest VARCHAR(64),
    ADD COLUMN stripe_pix_session_id VARCHAR(255),
    ADD COLUMN stripe_pix_amount_cents INT,
    ADD COLUMN stripe_pix_paid_until DATETIME(6);
