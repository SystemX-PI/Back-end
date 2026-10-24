ALTER TABLE usuario ADD COLUMN foto LONGTEXT;
CREATE TABLE password_reset_token (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, usuario_id BIGINT NOT NULL UNIQUE,
 token_hash VARCHAR(64) NOT NULL, expires_at DATETIME(6) NOT NULL,
 requested_at DATETIME(6) NOT NULL, used BOOLEAN NOT NULL DEFAULT FALSE,
 attempts INT NOT NULL DEFAULT 0, auth_version INT NOT NULL,
 CONSTRAINT fk_reset_usuario FOREIGN KEY(usuario_id) REFERENCES usuario(id)
);
