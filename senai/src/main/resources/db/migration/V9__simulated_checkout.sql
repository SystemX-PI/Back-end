CREATE TABLE platform_checkout (
 id VARCHAR(36) PRIMARY KEY, conta_id BIGINT NOT NULL,
 payment_method VARCHAR(10) NOT NULL, status VARCHAR(20) NOT NULL,
 amount_cents INT NOT NULL, created_at DATETIME(6) NOT NULL,
 expires_at DATETIME(6) NOT NULL, paid_at DATETIME(6),
 CONSTRAINT fk_checkout_conta FOREIGN KEY(conta_id) REFERENCES platform_account(id)
);
CREATE INDEX ix_checkout_conta ON platform_checkout(conta_id, payment_method, status);
