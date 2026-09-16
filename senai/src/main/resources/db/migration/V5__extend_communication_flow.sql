ALTER TABLE processo_contemplacao ADD COLUMN id_externo VARCHAR(255);
ALTER TABLE processo_contemplacao ADD COLUMN origem VARCHAR(255);
CREATE UNIQUE INDEX uk_processo_origem_externo ON processo_contemplacao (origem, id_externo);

ALTER TABLE comunicacao ADD COLUMN destinatario VARCHAR(255);
ALTER TABLE comunicacao ADD COLUMN assunto VARCHAR(255);
ALTER TABLE comunicacao ADD COLUMN mensagem VARCHAR(2000);
ALTER TABLE comunicacao ADD COLUMN criada_em TIMESTAMP(6);
ALTER TABLE comunicacao ADD COLUMN enviada_em TIMESTAMP(6);
ALTER TABLE comunicacao ADD COLUMN erro VARCHAR(255);