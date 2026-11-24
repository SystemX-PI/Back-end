ALTER TABLE comunicacao ADD COLUMN chave_operacao VARCHAR(48);
ALTER TABLE comunicacao ADD COLUMN remetente_solicitado_id BIGINT;
ALTER TABLE comunicacao ADD COLUMN provedor_mensagem_id VARCHAR(255);
CREATE UNIQUE INDEX uk_comunicacao_chave_operacao ON comunicacao (processo_id, canal, chave_operacao);
