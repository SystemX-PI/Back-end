ALTER TABLE auditoria ADD COLUMN conta_id BIGINT NULL;
CREATE INDEX ix_auditoria_conta_id ON auditoria (conta_id, id);
ALTER TABLE comunicacao ADD COLUMN conta_despacho_id BIGINT NULL;
