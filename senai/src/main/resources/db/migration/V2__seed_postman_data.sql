INSERT IGNORE INTO tipo_consorcio (id, nome) VALUES
    (1, 'AUTOMOVEL'),
    (2, 'MOTO'),
    (3, 'IMOVEL'),
    (4, 'SERVICOS');

INSERT IGNORE INTO consorcio (id, nome, tipo_id, ativo)
VALUES (1, 'Consorcio Automovel 2026', 1, TRUE);

INSERT IGNORE INTO grupo (id, numero, consorcio_id, quantidade_cotas, valor_parcela)
VALUES (1, 'GRUPO-001', 1, 100, 500.00);