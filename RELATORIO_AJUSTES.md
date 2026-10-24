# Relatório dos ajustes — SystemX

Data: 23/10/2026.

## Resultado

Foram corrigidos os acessos aos cadastros, a integração das telas de CRUD, a consulta do portal e o carregamento da fila. Frontend e backend continuam usando a porta **8080** para a API. Nenhuma dependência de aplicação ou serviço de infraestrutura foi acrescentado.

## O que foi ajustado

| Problema do diagnóstico | Ajuste aplicado |
|---|---|
| Cadastro público permitia escolher administrador | Criado `POST /api/auth/register`, que sempre cria CLIENTE. A criação de funcionários e administradores em `/api/usuarios` exige administrador autenticado. |
| CRUDs acessíveis a qualquer conta autenticada | Administradores gerenciam os cadastros. Funcionários consultam consórcios, tipos, grupos, cotas e clientes. Clientes acessam somente seu portal. |
| Token fixo habilitado | Desabilitado no Compose e na configuração padrão. O código só o aceita no perfil `local`, mediante ativação explícita e configuração do token. JWT_SECRET é obrigatório fora da configuração local fornecida. |
| PUT de usuário exigia senha | Atualização cadastral recebe nome, e-mail, perfil e estado ativo. A troca de senha usa uma operação separada. |
| Sessões continuavam utilizáveis após alterações de credenciais | Incluída uma versão de autenticação no usuário, JWT e refresh token. Trocar senha, e-mail, perfil ou estado ativo invalida as sessões anteriores. |
| Risco de perder todo acesso administrativo | Proteção contra excluir, desativar ou rebaixar o último administrador ativo. |
| Frontend não usava os novos CRUDs | Criada a tela `/admin/cadastros`, com tipos, consórcios, grupos e cotas. Telas de usuários/clientes também permitem manutenção conforme o perfil. Há consulta, criação, edição, confirmação de exclusão e troca de senha. |
| Seleção de vínculos exigia conhecer IDs | Formulários permitem buscar e selecionar tipo, consórcio ou grupo. |
| Portal era apenas uma estrutura visual | Criados endpoints e tela para listar os processos do cliente autenticado e consultar situação, responsável, cota, crédito, documentos e histórico reais. |
| Processos não tinham proprietário explícito | Adicionado `cliente_id`. Novas integrações gravam esse vínculo. A migration recupera vínculos antigos pelo destinatário da comunicação de e-mail. |
| Fila fazia duas consultas adicionais por processo | Processos são paginados e as últimas comunicações dos dois canais são buscadas em lote. O caminho paginado usa consulta da página, contagem quando necessária e busca em lote, em vez de duas consultas extras por registro. |
| Listas baixavam tabelas inteiras | Adicionados endpoints `/pagina` com busca no servidor, total, página e tamanho limitado a 100. Telas de manutenção e fila usam esses endpoints; a busca textual aguarda 300 ms de digitação. |
| Integrações podiam aguardar indefinidamente | Definidos limites de conexão/leitura para chamadas HTTP e conexão/leitura/escrita para SMTP. Os envios continuam síncronos. |
| Flyway era criado mesmo quando desabilitado nos testes | A configuração manual agora respeita `spring.flyway.enabled`. |
| Documentação divergente | Atualizados README, roteiro local e aviso de mudança de contrato no guia de Postman. O diagnóstico inicial aponta para este relatório. |

O cadastro público da interface foi atualizado para apresentar o perfil **Cliente**. A criação de integrantes da equipe fica na área administrativa. Não existe promoção automática do primeiro cadastro público.

## Construção, cache e consumo local

- O Dockerfile executa a suíte normalmente, sem `skipTests`.
- O cache BuildKit mantém as dependências Maven entre construções. Dependências/plugins ainda ausentes precisam ser baixados uma vez; limpar o cache exige baixá-los novamente.
- Sem alterações no código, `docker compose up -d` reutiliza a imagem e não recompila nem executa testes.
- Após alterações, `docker compose up --build -d` compila e testa a nova imagem.
- O `.dockerignore` exclui arquivos de construção e configuração particular do contexto enviado ao Docker.
- Os limites de execução continuam no arquivo local ignorado `docker-compose.override.yml`: API com 1 CPU/768 MB e MySQL com 0,5 CPU/512 MB. Esses limites não limitam globalmente o Docker nem a etapa de construção.
- Mantidos seis testes de integração Java. O teste redundante de inicialização foi removido; os testes restantes ganharam verificações dos comportamentos alterados, sem aumentar sua quantidade.
- Logs de inicialização dos testes foram reduzidos, mantendo falhas e avisos visíveis.

## Contratos alterados

| Operação | Contrato atual |
|---|---|
| Registro público | `POST /api/auth/register`: nome, email, senha. Retorna usuário CLIENTE. |
| Criar conta pela administração | `POST /api/usuarios`: nome, email, senha, tipo. Exige ADMINISTRADOR. |
| Atualizar usuário | `PUT /api/usuarios/{id}`: nome, email, tipo, ativo. |
| Trocar senha | `PUT /api/usuarios/{id}/senha`: senha. Exige ADMINISTRADOR. |
| Consultar clientes | `GET /api/clientes/pagina`. Exige ADMINISTRADOR ou FUNCIONARIO. |
| Listar cadastros | `GET /api/{recurso}/pagina?page=0&size=20&q=texto`. |
| Fila paginada | `GET /api/integracoes/comunicacoes/fila/pagina?page=0&size=20&q=texto&status=PENDENTE`. |
| Processos do próprio cliente | `GET /api/portal/processos` e `GET /api/portal/processos/{id}`. |

As respostas paginadas contêm `items`, `total`, `page` e `size`. Páginas da API começam em zero. As listas legadas na raiz mantêm o formato de array, limitado aos 100 registros mais recentes; consumidores que precisam percorrer todos os dados devem usar `/pagina`.

O portal retorna 404 ao consultar um processo que não pertence à conta. Documentos e histórico exibem até 100 registros recentes por processo. O portal oferece consulta, sem upload ou download de arquivos nesta alteração.

## Banco e atualização local

A migration **V7** adiciona a versão das sessões, o proprietário do processo e índices usados pela fila/portal. As migrations antigas foram preservadas.

Processos antigos sem comunicação de e-mail compatível permanecem sem cliente associado. Eles não são atribuídos por suposição nem expostos a uma conta aleatória. Esses casos precisam de conferência dos dados para associação posterior.

Administradores existentes continuam utilizáveis. Para um banco sem administrador, o perfil local permite criar o primeiro por `LOCAL_ADMIN_EMAIL` e `LOCAL_ADMIN_PASSWORD`, seguindo [Back-end/LOCAL.md](LOCAL.md). Não há senha administrativa padrão criada pelo código.

Na pasta `Back-end`, execute:

```powershell
docker compose up --build -d
```

Depois reinicie o frontend com `npm run dev` na pasta `Front-End`. O Swagger fica em `http://localhost:8080/swagger-ui/index.html`. Use o JWT obtido no login. Sessões anteriores podem exigir um novo login.

## Validação realizada

- **Backend:** duas execuções completas dos seis testes passaram, sem falhas ou testes pulados em cada rodada, usando Java 17 e o cache Maven existente. A rodada final tambem compilou o backend atualizado. Foram verificadas autenticação, rotação/revogação de refresh token, registro público sem promoção, permissões, CRUD, paginação, invalidação de sessão e isolamento do portal.
- **MySQL 8.0.46:** migrations V1 a V7 aplicadas com sucesso em um container temporário. Foi inserida uma comunicação antiga de exemplo antes da V7; o vínculo com o cliente foi recuperado corretamente. O índice composto da comunicação foi conferido. O container e seus dados temporários foram removidos.
- **Frontend:** TypeScript aprovado e construção de produção concluída.
- **Interface:** na primeira rodada, dez de onze testes passaram. O roteiro que percorre todas as telas excedeu o limite global de 30 segundos. Seu limite foi ajustado para 60 segundos. A nova rodada dos cenarios afetados, incluindo CRUD pela interface, nao chegou a executar: o servidor de teste excedeu seu prazo de inicializacao. Essa verificacao adicional permanece pendente; a execucao e o servidor de teste foram encerrados.
- **Verificação do pacote Java em modo offline:** interrompida porque `maven-jar-plugin:3.5.1` ainda não estava no cache local. Isso não é uma falha dos testes; a construção padrão com acesso ao repositório precisa obter esse plugin uma vez.
- **Revisão de arquivos:** diferenças verificadas sem erros de espaços em branco.

Não foi feita medição de carga ou latência com dados reais, nem envio real de e-mail/WhatsApp. O banco do projeto não foi alterado durante a validação das migrations.

## Decisões para manter o projeto simples

A consulta do usuário no banco a cada JWT foi mantida para respeitar imediatamente contas desativadas e sessões invalidadas. Não foi adicionado cache de autenticação sem medição que o justificasse.

Os envios continuam síncronos, agora com timeouts. Uma fila assíncrona só deve ser introduzida se medições reais mostrarem necessidade. Não foram adicionados Redis, mensageria ou novos containers permanentes.

Dashboard e indicadores da fila informam o recorte que representam: os 100 processos recentes no dashboard e a página atual na fila. Não são apresentados como totais globais.

Próximas verificações operacionais: reconstruir o ambiente local, entrar com uma conta administrativa, conferir os registros existentes e associar os processos legados que não puderam ser vinculados automaticamente. Medir consultas e latência com volume representativo antes de fazer novas mudanças de infraestrutura.
