ALTER TABLE comunicacao ADD COLUMN comunicado_oficial BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE comunicacao ADD COLUMN comunicado_original_id BIGINT;
ALTER TABLE comunicacao ADD CONSTRAINT fk_comunicacao_original
    FOREIGN KEY (comunicado_original_id) REFERENCES comunicacao(id) ON DELETE SET NULL;
