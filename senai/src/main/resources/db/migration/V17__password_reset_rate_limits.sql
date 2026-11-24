CREATE TABLE password_reset_rate_limit (
    bucket VARCHAR(64) PRIMARY KEY,
    hits INT NOT NULL,
    expires_at BIGINT NOT NULL
);
CREATE INDEX ix_password_reset_rate_limit_expiry ON password_reset_rate_limit (expires_at);
