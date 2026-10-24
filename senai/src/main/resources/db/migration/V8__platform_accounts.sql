CREATE TABLE platform_account (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, nome VARCHAR(255) NOT NULL,
 owner_email VARCHAR(255), brand_name VARCHAR(255) NOT NULL, logo LONGTEXT,
 primary_color VARCHAR(255) NOT NULL, accent_color VARCHAR(255) NOT NULL,
 created_at DATETIME(6) NOT NULL, trial_ends_at DATETIME(6) NOT NULL,
 paid_until DATETIME(6), suspended BOOLEAN NOT NULL DEFAULT FALSE
);
ALTER TABLE usuario ADD COLUMN conta_id BIGINT;
ALTER TABLE usuario ADD CONSTRAINT fk_usuario_conta FOREIGN KEY(conta_id) REFERENCES platform_account(id);
CREATE INDEX ix_usuario_conta ON usuario(conta_id);
CREATE TABLE platform_instance (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, conta_id BIGINT NOT NULL,
 nome VARCHAR(60) NOT NULL, remote_name VARCHAR(255) NOT NULL UNIQUE,
 CONSTRAINT fk_instance_conta FOREIGN KEY(conta_id) REFERENCES platform_account(id),
 CONSTRAINT uq_instance_nome UNIQUE(conta_id,nome)
);
CREATE TABLE platform_mailbox (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, conta_id BIGINT NOT NULL,
 nome VARCHAR(255) NOT NULL, email VARCHAR(255) NOT NULL,
 encrypted_password VARCHAR(2000) NOT NULL, verified BOOLEAN NOT NULL,
 CONSTRAINT fk_mailbox_conta FOREIGN KEY(conta_id) REFERENCES platform_account(id),
 CONSTRAINT uq_mailbox_email UNIQUE(conta_id,email)
);
-- Preserve the already connected legacy instance without logging it out.
INSERT INTO platform_account(nome,brand_name,primary_color,accent_color,created_at,trial_ends_at,paid_until,suspended)
 VALUES('Operação existente','ConsorTech','#315fe8','#35cda0',CURRENT_TIMESTAMP,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 3 DAY),NULL,FALSE);
INSERT INTO platform_instance(conta_id,nome,remote_name) SELECT id,'consorcio','consorcio' FROM platform_account WHERE nome='Operação existente';
-- Existing customer logins retain access through their own new account.
INSERT INTO platform_account(nome,owner_email,brand_name,primary_color,accent_color,created_at,trial_ends_at,suspended)
 SELECT nome,email,nome,'#315fe8','#35cda0',CURRENT_TIMESTAMP,DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 3 DAY),FALSE FROM usuario WHERE tipo='CLIENTE';
UPDATE usuario u JOIN platform_account a ON a.owner_email=u.email SET u.conta_id=a.id WHERE u.tipo='CLIENTE';
