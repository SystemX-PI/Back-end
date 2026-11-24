-- V19 usava INCERTO tanto para execução expirada quanto para resposta ambígua.
UPDATE comunicacao
SET estado_despacho = 'INCERTO_EXECUCAO'
WHERE estado_despacho = 'INCERTO';

-- Recupera somente tentativas comprovadamente não iniciadas e sem outra tentativa no mesmo canal.
UPDATE comunicacao
SET estado_despacho = 'NA_FILA', despacho_iniciado_em = NULL, erro = NULL
WHERE id IN (
    SELECT recuperavel.id FROM (
        SELECT DISTINCT c.id
        FROM comunicacao c
        WHERE c.estado_despacho = 'CONCLUIDO'
          AND c.status = 'PENDENTE'
          AND c.erro IN ('Canal de e-mail indisponível.', 'Canal WhatsApp indisponível.')
          AND c.enviada_em IS NULL AND c.provedor_mensagem_id IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM comunicacao outra
              WHERE outra.processo_id = c.processo_id AND outra.canal = c.canal
                AND outra.id <> c.id AND outra.estado_despacho IS NOT NULL
          )
    ) recuperavel
);
