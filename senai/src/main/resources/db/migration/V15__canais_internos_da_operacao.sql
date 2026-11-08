ALTER TABLE platform_account ADD COLUMN internal_operations BOOLEAN UNIQUE;

INSERT INTO platform_account
    (nome, brand_name, primary_color, accent_color, created_at, trial_ends_at, internal_operations)
VALUES ('Operação interna', 'SystemX', '#315fe8', '#35cda0', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TRUE);

ALTER TABLE auditoria DROP FOREIGN KEY fk_aud_usuario;
ALTER TABLE auditoria ADD CONSTRAINT fk_aud_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE SET NULL;
