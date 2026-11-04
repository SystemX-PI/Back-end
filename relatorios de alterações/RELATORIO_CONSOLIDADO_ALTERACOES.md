# Relatório consolidado de alterações — SystemX Back-end

Data de consolidação: 04/11/2026
Base consultada: documentos versionados na branch `develop` do repositório `SystemX-PI/Back-end`.

## 1. Objetivo e fontes consultadas

Este documento reúne as informações dispersas nos documentos do projeto para servir como referência única de produto, segurança, operação local, contratos de API, testes e próximos cuidados. Os documentos originais não foram removidos: eles continuam sendo a fonte detalhada de cada assunto.

| Documento de origem | Papel no projeto |
| --- | --- |
| `README.md` | Visão geral do backend, execução Docker, autenticação, Google, testes e fluxo de colaboração. |
| `LOCAL.md` | Operação local, variáveis, Docker, Evolution API, Stripe, Gmail e diagnóstico. |
| `RELATORIO_AJUSTES.md` | Ajustes de segurança, CRUD, portal, fila, paginação e migration V7. |
| `RELATORIO_PLATAFORMA.md` | Evolução para plataforma multi-conta, integrações, assinatura, perfil e migrations V8 a V11. |
| `senai/GUIA_TESTES_POSTMAN.md` | Roteiro de validação manual de autenticação, CRUD, integrações e respostas esperadas. |

> **Leitura importante:** parte do material registra entregas em momentos diferentes. Por isso, as quantidades de testes e alguns comandos refletem a data de cada relatório. Para operar hoje, priorize o `README.md` e o `LOCAL.md`; para entender o motivo de uma regra, consulte os relatórios de ajustes e plataforma.

## 2. Visão atual da solução

O SystemX é uma API Spring Boot voltada a uma plataforma por contas de clientes. Ela oferece autenticação, gestão de usuários e equipes, marca, integrações com WhatsApp e Gmail, período de teste, checkout demonstrativo e módulos legados de consórcios.

O ambiente de desenvolvimento usa Docker Compose. O frontend é compilado e servido por Nginx; as chamadas em `/api` seguem internamente para o backend na porta 8080. O MySQL atende a aplicação principal e a Evolution API tem PostgreSQL e Redis próprios na rede Docker. As imagens são construídas na máquina de cada pessoa a partir dos repositórios atualizados; um push no GitHub não publica uma imagem automaticamente.

## 3. Evolução funcional consolidada

### 3.1 Segurança, cadastro e autorização

As mudanças principais nessa área foram:

- O registro público passou a criar exclusivamente uma conta do tipo `CLIENTE` por `POST /api/auth/register`. Não existe promoção automática para administrador.
- A criação de administradores e funcionários foi concentrada em `POST /api/usuarios`, acessível somente a um administrador autenticado.
- As permissões foram separadas por perfil: administradores gerenciam cadastros; funcionários consultam recursos permitidos; clientes acessam apenas o próprio espaço e portal.
- O token fixo de desenvolvimento foi desabilitado por padrão. Ele só pode existir no perfil local com ativação explícita e nunca deve ser usado fora desse ambiente.
- Senha, e-mail, tipo de usuário ou estado ativo alterados invalidam sessões existentes por meio de uma versão de autenticação associada ao usuário, JWT e refresh token.
- Foi criada uma proteção para impedir que o último administrador ativo seja removido, desativado ou rebaixado.
- A alteração cadastral deixou de aceitar senha no `PUT` de usuário. A troca de senha ocorre em uma rota específica, reduzindo o risco de alteração acidental.

### 3.2 Portal, cadastros e fila operacional

Os módulos de cadastro e portal receberam mudanças para deixar de ser apenas telas visuais:

- A área administrativa ganhou manutenção de tipos, consórcios, grupos, cotas, usuários e clientes, incluindo criação, edição, exclusão confirmada e troca de senha.
- Os formulários deixaram de exigir que a pessoa conheça IDs internos: vínculos como tipo, consórcio e grupo podem ser pesquisados e selecionados.
- O portal do cliente passou a mostrar processos reais pertencentes à conta autenticada, incluindo situação, responsável, cota, crédito, documentos e histórico.
- Cada processo passou a ter `cliente_id`. A migration tenta associar registros antigos pelo destinatário de e-mail e mantém sem vínculo os casos ambíguos, evitando exposição de dados entre contas.
- Foram criadas rotas paginadas com busca no servidor e tamanho máximo de 100 itens. A fila também passou a buscar as últimas comunicações em lote, reduzindo consultas repetidas por processo.
- Chamadas HTTP e SMTP receberam timeouts. Os envios permanecem síncronos; fila assíncrona ou mensageria não foram adicionadas sem uma medição real que as justifique.

### 3.3 Plataforma por contas de clientes

A evolução registrada no relatório da plataforma introduziu o modelo multi-conta:

- **Administrador:** gerencia contas de clientes e consulta totais de recursos.
- **Cliente:** titular de uma conta, responsável por marca, equipe, integrações e assinatura.
- **Usuário:** integrante da equipe, limitado aos recursos da própria conta.

O cadastro público cria uma empresa e o respectivo titular com três dias de teste calculados no servidor. Os e-mails de login continuam únicos em toda a plataforma. Cada endpoint resolve a conta a partir do usuário autenticado; somente administrador pode selecionar outra conta de forma explícita.

As áreas principais da plataforma são `/app`, `/app/integracoes`, `/app/whatsapp`, `/app/gmail`, `/app/equipe`, `/app/marca` e `/app/assinatura`. A interface foi adaptada para telas de 360, 390, 768 e 1440 pixels, incluindo navegação recolhível e tema escuro.

### 3.4 WhatsApp, Gmail e marca

As integrações receberam isolamento por conta:

- Cada instância de WhatsApp pertence a uma conta. O nome visível pode se repetir entre clientes, mas o identificador externo é único e inclui a conta.
- O cliente pode adicionar, selecionar, gerar QR, desconectar, reconectar e remover instâncias com confirmação.
- O estado e QR da Evolution API são consultados periodicamente durante a conexão; a consulta termina ao conectar, após três minutos ou após falhas consecutivas.
- As caixas Gmail são configuradas por conta. Senhas de aplicativo são verificadas antes de salvar, cifradas com AES-GCM no servidor e nunca retornam para o frontend.
- Marca, logo e cores pertencem à conta e são compartilhados com a equipe autenticada. Domínio próprio e páginas públicas personalizadas ainda não fazem parte da entrega.

### 3.5 Teste gratuito e checkout demonstrativo

O produto possui teste de 72 horas. Durante esse período, há limite de uma instância WhatsApp e uma conta Gmail. Quando o teste termina, operações de configuração são bloqueadas com HTTP 402; consultas e remoção/desconexão continuam disponíveis para evitar retenção indevida de recursos.

O pagamento ainda é uma demonstração: o checkout permite escolher Pix ou cartão fictício, cria um pedido pendente e exige confirmação explícita. Apenas a confirmação libera o plano Profissional por 30 dias, com três WhatsApps e dois Gmails. Repetir a confirmação não estende o plano. Não existe cobrança real, webhook comercial ou preço definitivo implementado.

## 4. Banco de dados e migrations

As migrations citadas nos documentos preservam dados anteriores e evoluem o schema sem recriar o banco:

| Migration | Alteração registrada |
| --- | --- |
| V7 | Versão de autenticação, proprietário de processo e índices para fila/portal. |
| V8 | Contas da plataforma, instâncias, caixas Gmail e vínculo de usuários às contas. |
| V9 | Persistência de pedidos de checkout. |
| V10 | Diferença entre compra confirmada, demonstração e datas antigas de ativação. |
| V11 | Foto do usuário e registros de recuperação de senha. |

Os módulos legados de consórcios continuam no código e seus dados não foram apagados. Processos antigos sem associação confiável de cliente permanecem sem proprietário até revisão manual. Esta é uma escolha de segurança: é preferível ter um registro sem acesso no portal do que entregá-lo à conta errada.

## 5. Contratos e rotas relevantes

| Finalidade | Rota ou contrato atual |
| --- | --- |
| Registro público | `POST /api/auth/register` com nome, e-mail e senha; cria `CLIENTE`. |
| Login e sessão | `POST /api/auth/login`, `POST /api/auth/refresh` e `POST /api/auth/logout`. |
| Gestão administrativa | `POST /api/usuarios`, `PUT /api/usuarios/{id}` e `PUT /api/usuarios/{id}/senha`. |
| Clientes | `GET /api/clientes/pagina`, para administrador ou funcionário. |
| Listagens paginadas | `GET /api/{recurso}/pagina?page=0&size=20&q=texto`. |
| Fila paginada | `GET /api/integracoes/comunicacoes/fila/pagina?page=0&size=20&q=texto&status=PENDENTE`. |
| Portal do cliente | `GET /api/portal/processos` e `GET /api/portal/processos/{id}`. |
| Plataforma multi-conta | Rotas sob `/api/platform` para contexto, clientes, instâncias, caixas de e-mail, usuários, marca e assinatura. |
| Integração de contemplados | `POST /api/integracoes/contemplacoes`, com chave de integração. |
| Comunicação e baixa | Rotas de consulta/reenvio de comunicação e baixa por processo. |

As respostas paginadas têm os campos `items`, `total`, `page` e `size`, com a primeira página em zero. As rotas legadas que retornam arrays foram mantidas por compatibilidade, mas têm limite de 100 registros recentes. Clientes que precisam percorrer todos os dados devem migrar para a versão paginada.

## 6. Operação local consolidada

### Estrutura esperada

O ambiente completo depende de dois repositórios lado a lado:

```text
projeto/
  Back-end/
  Front-End/
```

Na pasta `Back-end`, o fluxo indicado para reconstruir e aguardar a saúde dos serviços é:

```powershell
docker compose --profile full up --build -d --wait --wait-timeout 600
```

Para atualizar, faça `git pull --ff-only` nos dois repositórios e repita o comando. Para ligar uma versão já construída, use o mesmo comando sem `--build`. `docker compose --profile full down` interrompe os serviços preservando volumes; `down -v` remove os dados locais e só deve ser usado quando isso for desejado.

### Endereços e diagnóstico

- Frontend: `http://localhost:5173`.
- Backend/Swagger: `http://localhost:8080/swagger-ui/index.html`.
- MySQL local: porta `3307`, exclusivamente para desenvolvimento.
- Evolution API: porta `8081`; backend acessa o serviço pela rede Docker.

Comandos de diagnóstico recomendados:

```powershell
docker compose --profile full ps
docker compose --profile full logs --tail=80
docker stats --no-stream
docker compose config --quiet
```

O cache do BuildKit mantém dependências Maven e npm. Não use `--no-cache` ou limpeza de volumes/caches na rotina normal: eles aumentam o tempo de build e não corrigem problemas de código.

### Variáveis e segredos

Arquivos `.env`, senhas de banco, `JWT_SECRET`, chaves da Evolution API, credenciais Gmail, chaves Stripe e tokens de integração não devem ser versionados. Cada pessoa cria suas variáveis locais a partir dos exemplos do repositório. Em qualquer ambiente público, os valores de desenvolvimento devem ser substituídos por credenciais exclusivas e rotacionáveis.

O Google Sign-In usa Client ID no frontend e backend. O Client ID é público; o Client Secret não deve ser colocado no código ou navegador. Em produção, as origens JavaScript autorizadas precisam corresponder exatamente ao domínio HTTPS publicado.

## 7. Recuperação de senha e perfil

O perfil permite editar nome e foto, com prévia, redimensionamento e persistência no backend. A troca de senha exige a senha atual, invalida sessões anteriores e entrega uma sessão nova para manter o usuário atual conectado.

A recuperação de senha usa código de oito dígitos, uso único, expiração de cinco minutos e proteção contra excesso de tentativas e solicitações. O servidor guarda apenas um HMAC do código e não revela se o e-mail informado existe. Sem um remetente Gmail configurado, o sistema informa que o envio está indisponível; não simula sucesso.

## 8. Testes e evidências registradas

Os relatórios registram rodadas de teste em pontos diferentes da evolução:

- No relatório de ajustes: duas execuções da suíte de seis testes, migrations V1 a V7 verificadas em MySQL temporário, TypeScript e build de produção aprovados. Uma verificação adicional de navegador ficou pendente por prazo de inicialização do servidor de teste.
- No relatório da plataforma: 10 testes de backend após a entrega multi-conta, mais quatro testes de isolamento, permissões, período de teste e ciclo de conexão; sete cenários de frontend aprovados.
- Na etapa de checkout: 11 testes de backend e seis cenários de navegador registrados como aprovados.
- Na etapa de perfil/recuperação: total de 15 testes de backend e quatro cenários de navegador focados registrados como aprovados.

Esses números não devem ser somados. Eles representam a suíte em momentos distintos. Antes de qualquer merge, a referência deve ser o resultado obtido na branch atual pelo CI e pela execução local de `mvn test` ou pelo build Docker completo.

O guia de Postman complementa a automação com casos de autenticação, renovação e revogação de token, validação de entradas, recursos inexistentes, CRUD, integração de contemplados, comunicação, reenvio e baixa de processo.

## 9. Pontos de atenção e pendências conhecidas

1. **Documentação histórica:** alguns trechos ainda descrevem módulos legados e comandos antigos. Eles são úteis como histórico, mas o fluxo com `--profile full` deve ser priorizado para subir a solução completa.
2. **Cobrança real:** Stripe e webhook comercial não estão concluídos; o checkout descrito é simulado.
3. **E-mail real:** a recuperação foi validada com remetente simulado. É necessário validar o fluxo com uma caixa Gmail de teste antes de disponibilizá-lo externamente.
4. **Integrações externas:** a conexão real do WhatsApp exige QR aprovado no aparelho. Não há garantia de entrega/leitura de mensagens apenas porque a Evolution ou SMTP aceitou o envio.
5. **Dados legados:** processos sem associação segura de cliente devem ser revisados manualmente antes de liberá-los no portal.
6. **Carga e latência:** não há medição com volume real. Antes de introduzir cache, Redis adicional, mensageria ou alteração de infraestrutura, medir gargalos reais.
7. **Qualidade de código:** os documentos versionados consultados não continham um protocolo de SonarQube. Um protocolo local de análise foi adicionado ao `README.md` na árvore de trabalho, mas precisa ser revisado e versionado por Pull Request para se tornar regra oficial.
8. **Publicação externa:** Compose local não é, por si só, uma configuração de produção. Em AWS, usar variáveis seguras, domínio HTTPS, banco protegido, backups, logs, firewall e portas mínimas expostas.

## 10. Procedimento recomendado para mudanças futuras

1. Criar uma branch `feature/<nome>` ou `fix/<nome>` a partir de `develop`.
2. Atualizar código, migration e documentação que forem afetados.
3. Executar testes Maven e o build/Compose necessário.
4. Executar a análise do SonarQube e revisar issues críticas, segurança e Quality Gate.
5. Abrir Pull Request para `develop` descrevendo objetivo, riscos, testes e impacto em contratos.
6. Fazer merge em `main` apenas depois da homologação em `develop` e da revisão aprovada.
7. Atualizar este relatório sempre que houver uma entrega relevante, mantendo data, escopo, evidências e pendências.

## 11. Conclusão

O projeto evoluiu de uma API de consórcios para uma plataforma multi-conta com isolamento de dados, integração de canais, gestão de equipe, teste gratuito e uma etapa demonstrativa de assinatura. As mudanças priorizaram autorização, segregação por conta, preservação de dados existentes, operação com Docker e contratos mais claros.

O próximo avanço deve concentrar-se em consolidar a documentação operacional, manter o CI/SonarQube como requisito de merge, validar as integrações externas com credenciais de teste e preparar uma configuração própria para homologação ou produção na AWS.
