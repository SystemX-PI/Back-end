# Resumo de notificações por conta e período

Implementação alternativa e corrigida para a parte de histórico do PR #110, relacionada à issue #105. A consulta da plataforma mantém o escopo da conta autenticada; somente o administrador pode escolher outra conta. A consulta operacional global mantém a autorização de administrador/funcionário já definida na API.

- `GET /api/platform/notification-history?start=2026-11-01&end=2026-11-05&channel=EMAIL` (`accountId` opcional para administrador).
- `GET /api/integracoes/comunicacoes/historico?inicio=2026-11-01&fim=2026-11-05&canal=EMAIL`.

O canal é opcional. As datas são inclusivas, com até 366 dias no total, no horário UTC usado pela persistência. Retorna `startDate`, `endDate`, `channel`, `total`, `accepted`, `pending`, `failed` e até vinte `items`, ordenados por criação e ID decrescentes. Os totais são agregados no banco; somente vinte registros são carregados para montar os itens.

Cada `Comunicacao` é contada uma única vez, pelo estado atual e pelo período em que foi criada. Reenvios da mesma comunicação não aumentam `total`; um novo registro de comunicação representa uma nova notificação. `accepted` inclui `SUCESSO` e `REALIZADA` e significa aceite do envio, não confirmação de entrega. `failed` inclui `FALHA`. `PENDENTE` e `SEM_RESPOSTA` ficam em `pending` por ainda não representarem aceite ou falha confirmados.

Esta consulta é um resumo do estado atual das notificações, não um registro imutável de cada tentativa. O histórico persistente de tentativas continua no escopo da issue #66; esta PR não fecha aquele card nem inventa tentativas antigas. Os erros públicos usam mensagens controladas e não copiam diagnósticos técnicos ou corpos de respostas de provedores.
