ALTER TABLE comunicacao ADD COLUMN estado_despacho VARCHAR(24);
ALTER TABLE comunicacao ADD COLUMN despacho_iniciado_em DATETIME(6);
ALTER TABLE comunicacao ADD COLUMN instancia_despacho VARCHAR(255);
CREATE INDEX ix_comunicacao_outbox ON comunicacao (estado_despacho, id);
CREATE INDEX ix_comunicacao_outbox_processo ON comunicacao (processo_id, estado_despacho);
