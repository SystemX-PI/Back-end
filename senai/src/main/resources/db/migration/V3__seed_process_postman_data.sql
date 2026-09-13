INSERT IGNORE INTO usuario (id, nome, email, senha, tipo, ativo)
VALUES (1, 'Usuario Postman', 'usuario.postman@example.com', 'senha123', 'CLIENTE', TRUE);

INSERT IGNORE INTO cota (id, numero, grupo_id, valor_credito, ativa)
VALUES (1, 'COTA-001', 1, 50000.00, TRUE);

INSERT IGNORE INTO assembleia (id, grupo_id, data, observacao)
VALUES (1, 1, '2026-09-13', 'Assembleia de teste do Postman');

INSERT IGNORE INTO contemplacao (id, cota_id, assembleia_id, data, tipo, valor)
VALUES (1, 1, 1, '2026-09-13', 'SORTEIO', 50000.00);

INSERT IGNORE INTO processo_contemplacao (id, contemplacao_id, responsavel_id, status, criado_em)
VALUES (1, 1, NULL, 'CONTEMPLADO', CURRENT_TIMESTAMP(6));