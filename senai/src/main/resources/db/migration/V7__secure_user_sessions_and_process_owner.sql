ALTER TABLE usuario ADD COLUMN versao_autenticacao INT NOT NULL DEFAULT 0;
ALTER TABLE processo_contemplacao ADD COLUMN cliente_id BIGINT NULL;
UPDATE processo_contemplacao p
JOIN comunicacao c ON c.id = (
    SELECT MIN(c2.id) FROM comunicacao c2 WHERE c2.processo_id = p.id AND c2.canal = 'EMAIL'
)
JOIN usuario u ON u.email = c.destinatario
SET p.cliente_id = u.id;
ALTER TABLE processo_contemplacao ADD CONSTRAINT fk_processo_cliente FOREIGN KEY (cliente_id) REFERENCES usuario(id);
CREATE INDEX idx_processo_cliente_criado ON processo_contemplacao (cliente_id, criado_em);
CREATE INDEX idx_comunicacao_processo_canal_id ON comunicacao (processo_id, canal, id);
ALTER TABLE refresh_token ADD COLUMN versao_autenticacao INT NOT NULL DEFAULT 0;
