CREATE TABLE contemplado_empresa (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 conta_id BIGINT NOT NULL,
 origem VARCHAR(100) NOT NULL,
 referencia VARCHAR(150) NOT NULL,
 nome VARCHAR(150) NOT NULL,
 telefone VARCHAR(30),
 email VARCHAR(255),
 recebido_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE (conta_id, origem, referencia),
 FOREIGN KEY (conta_id) REFERENCES platform_account(id)
);
CREATE TABLE contemplado_envio (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 contemplado_id BIGINT NOT NULL,
 canal VARCHAR(12) NOT NULL,
 estado VARCHAR(12) NOT NULL,
 criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (contemplado_id) REFERENCES contemplado_empresa(id)
);
ALTER TABLE platform_account ADD COLUMN contemplados_chave_hash CHAR(64) UNIQUE;
