# Guia de testes no Postman

Este documento descreve como testar a API do backend do projeto pelo Postman e resume o que foi implementado.

## 1. Pré-requisitos

- Aplicação Spring Boot em execução na porta `8080`.
- MySQL em execução.
- Banco configurado conforme `src/main/resources/application.properties`:
	- banco: `senai`
	- usuário padrão: `root`
	- senha padrão: `root`
- O Flyway executa as migrations `V1` até `V4` na inicialização.
- As migrations `V2__seed_postman_data.sql` e `V3__seed_process_postman_data.sql` criam dados-base para os testes de consórcio, cota e processo.
- A migration `V4__create_refresh_tokens.sql` cria a tabela usada pelos refresh tokens.

Depois de alterar o código, pare a execução antiga no Eclipse e inicie novamente o projeto. Se a porta `8080` já estiver ocupada, não inicie uma segunda instância: use a aplicação que já está rodando ou encerre a execução anterior antes de iniciar outra.

No Postman, crie um ambiente com a variável:

```text
baseUrl = http://localhost:8080
```

Todas as requisições que enviam JSON devem usar o cabeçalho:

```text
Content-Type: application/json
```

As rotas de autenticação e criação de usuário são públicas. As demais rotas exigem um JWT no cabeçalho:

```text
Authorization: Bearer SEU_ACCESS_TOKEN
```

No Postman, a forma mais simples é abrir a aba `Authorization`, escolher `Bearer Token` e colar somente o valor do campo `token` retornado pelo login. O Postman montará o cabeçalho automaticamente.

## 2. Autenticação no Swagger

Acesse `http://localhost:8080/swagger-ui/index.html` ou `http://localhost:8080/documentacao`.

Não existe um token universal fixo. Cada login gera um access token assinado para o usuário. Para testar pelo Swagger:

1. Execute `POST /api/auth/login` usando o botão `Try it out`.
2. Use um usuário criado por `POST /api/usuarios` ou os dados existentes no seu banco.
3. Copie somente o valor de `token` da resposta, sem aspas.
4. Clique em `Authorize`, no topo da página.
5. Cole o token no campo `Value` e clique em `Authorize` e depois em `Close`.
6. Execute as rotas protegidas normalmente. O Swagger enviará automaticamente `Authorization: Bearer <token>`.

O botão `Authorize` foi configurado como `bearerAuth` e aceita somente o JWT. Não cole a palavra `Bearer` junto no campo do Swagger, pois a interface já adiciona esse prefixo.

### Token universal para testes locais

Para não precisar fazer login a cada teste, existe um token fixo opcional somente para desenvolvimento. Ele fica desativado por padrão. Inicie a aplicação com estas variáveis:

No Windows CMD:

```bat
set DEV_TOKEN_ENABLED=true
set DEV_API_TOKEN=token-local-123
mvn spring-boot:run
```

Ou configure no ambiente de execução:

```properties
app.security.dev-token-enabled=true
app.security.dev-token=token-local-123
```

Depois, no Swagger, clique em `Authorize` e informe apenas:

```text
token-local-123
```

O Curl correto gerado para uma rota protegida é:

```bash
curl -X GET "http://localhost:8080/api/usuarios" \
	-H "accept: */*" \
	-H "Authorization: Bearer token-local-123"
```

Se aparecer `Authorization: Bearer Bearer token-local-123`, abra novamente `Authorize` e apague a palavra `Bearer` do campo. O Swagger acrescenta `Bearer` automaticamente. Também reinicie a aplicação depois de alterar `DEV_TOKEN_ENABLED` ou `DEV_API_TOKEN`; o Spring lê essas variáveis apenas ao iniciar.

No Postman, selecione `Bearer Token` e informe o mesmo valor. A requisição enviada será:

```http
Authorization: Bearer token-local-123
```

Esse token representa um usuário técnico com permissão de administrador e pode acessar as rotas protegidas sem consultar o usuário no banco. Não use essa opção em produção e não publique esse valor no Git. Sem as variáveis acima, somente o JWT real do login será aceito.

Se uma rota protegida for executada sem autorização, a resposta esperada é `401 Unauthorized`. Se o token estiver expirado, inválido ou tiver sido copiado incorretamente, faça login novamente e autorize o Swagger com o novo token.

## 3. Ordem recomendada dos testes

1. Criar um usuário.
2. Listar usuários e guardar o `id` retornado.
3. Confirmar no banco um `tipo_consorcio` existente e guardar seu `id`.
4. Criar um consórcio usando esse `tipoId`.
5. Confirmar no banco um `grupo` existente e guardar seu `id`.
6. Criar uma cota usando esse `grupoId`.
7. Consultar um processo de contemplação existente.
8. Atualizar o status do processo usando o `id` do usuário criado.

Os endpoints para criar tipo de consórcio, grupo, contemplação e processo ainda não foram expostos. Por isso, esses registros precisam existir previamente no banco para os testes de consórcio, cota e processo.

## 4. Testes de autenticação

### 4.1 Criar usuário

**POST** `{{baseUrl}}/api/usuarios`

Esta rota é pública para permitir o cadastro inicial. Body > raw > JSON:

```json
{
	"nome": "Maria da Silva",
	"email": "maria.silva@example.com",
	"senha": "senha123",
	"tipo": "CLIENTE"
}
```

Resultado esperado: `201 Created`. A senha não aparece na resposta e é armazenada usando BCrypt, nunca em texto puro.

### 4.2 Fazer login

**POST** `{{baseUrl}}/api/auth/login`

Body:

```json
{
	"email": "maria.silva@example.com",
	"senha": "senha123"
}
```

Resultado esperado: `200 OK`.

```json
{
	"token": "eyJ...",
	"refreshToken": "...",
	"tipo": "CLIENTE",
	"expiresIn": 900
}
```

O `token` é o access token JWT usado nas requisições protegidas. Neste exemplo, `expiresIn: 900` significa 900 segundos, ou 15 minutos. O `refreshToken` dura 7 dias por padrão e serve somente para obter outro access token.

### 4.3 Renovar o access token

**POST** `{{baseUrl}}/api/auth/refresh`

Body:

```json
{
	"refreshToken": "COLE_AQUI_O_REFRESH_TOKEN"
}
```

Resultado esperado: `200 OK`, com um novo `token` e um novo `refreshToken`. O refresh token anterior é invalidado após a rotação; substitua os dois valores salvos no Postman.

### 4.4 Fazer logout

**POST** `{{baseUrl}}/api/auth/logout`

Body:

```json
{
	"refreshToken": "COLE_AQUI_O_REFRESH_TOKEN_ATUAL"
}
```

Resultado esperado: `204 No Content`. O refresh token informado deixa de poder ser usado. O access token já emitido continua válido até expirar, pois JWT é stateless; para bloquear imediatamente um access token seria necessário implementar uma blacklist.

### 4.5 Usar uma rota protegida

**GET** `{{baseUrl}}/api/consorcios`

No Postman, em `Authorization`, selecione `Bearer Token` e informe o `token` do login. Não use o `refreshToken` nesta aba. Resultado esperado: `200 OK` com o token válido.

Sem esse header, resultado esperado: `401 Unauthorized`.

## 5. Testes no Postman

### 5.1 Criar usuário

**POST** `{{baseUrl}}/api/usuarios`

Body > raw > JSON:

```json
{
	"nome": "Maria da Silva",
	"email": "maria.silva@example.com",
	"senha": "senha123",
	"tipo": "CLIENTE"
}
```

Valores aceitos para `tipo`: `ADMINISTRADOR`, `FUNCIONARIO` e `CLIENTE`.

Resultado esperado: `201 Created`.

```json
{
	"id": 1,
	"nome": "Maria da Silva",
	"email": "maria.silva@example.com",
	"tipo": "CLIENTE",
	"ativo": true
}
```

O campo `senha` é usado na criação, mas não é devolvido na resposta.

### 5.2 Listar usuários

**GET** `{{baseUrl}}/api/usuarios`

Resultado esperado: `200 OK`, com uma lista de usuários.

### 5.3 Criar consórcio

Antes deste teste, consulte o banco para obter um ID válido:

```sql
SELECT id, nome FROM tipo_consorcio;
```

**POST** `{{baseUrl}}/api/consorcios`

Body:

```json
{
	"nome": "Consórcio Automóvel 2026",
	"tipoId": 1
}
```

Resultado esperado: `201 Created`.

```json
{
	"id": 1,
	"nome": "Consórcio Automóvel 2026",
	"tipoId": 1,
	"tipoNome": "Automóvel",
	"ativo": true
}
```

O `tipoId` precisa existir em `tipo_consorcio`; caso contrário, a API retorna `404 Not Found`.

### 5.4 Listar consórcios

**GET** `{{baseUrl}}/api/consorcios`

Resultado esperado: `200 OK`, com uma lista de consórcios.

### 5.5 Criar cota

Antes deste teste, consulte o banco para obter um grupo válido:

```sql
SELECT id, numero, consorcio_id FROM grupo;
```

**POST** `{{baseUrl}}/api/cotas`

Body:

```json
{
	"numero": "COTA-001",
	"grupoId": 1,
	"valorCredito": 50000.00
}
```

Resultado esperado: `201 Created`.

```json
{
	"id": 1,
	"numero": "COTA-001",
	"grupoId": 1,
	"grupoNumero": "GRUPO-001",
	"valorCredito": 50000.00,
	"ativa": true
}
```

O `grupoId` precisa existir em `grupo`; caso contrário, a API retorna `404 Not Found`.

### 5.6 Listar cotas

**GET** `{{baseUrl}}/api/cotas`

Resultado esperado: `200 OK`, com uma lista de cotas.

### 5.7 Consultar processo de contemplação

Antes deste teste, consulte um processo existente:

```sql
SELECT id, contemplacao_id, status FROM processo_contemplacao;
```

**GET** `{{baseUrl}}/api/processos-contemplacao/1`

Resultado esperado: `200 OK`.

```json
{
	"id": 1,
	"contemplacaoId": 1,
	"responsavelId": null,
	"status": "CONTEMPLADO",
	"criadoEm": "2026-09-12T10:30:00",
	"atualizadoEm": null
}
```

### 5.8 Atualizar status do processo

**PATCH** `{{baseUrl}}/api/processos-contemplacao/1/status`

Body:

```json
{
	"status": "AGUARDANDO_COMUNICACAO",
	"observacao": "Processo encaminhado para contato com o cliente.",
	"usuarioId": 1
}
```

Valores aceitos para `status`:

`CONTEMPLADO`, `AGUARDANDO_COMUNICACAO`, `EM_COMUNICACAO`, `CLIENTE_CONTATADO`, `AGUARDANDO_DOCUMENTOS`, `DOCUMENTOS_RECEBIDOS`, `EM_ANALISE`, `EM_PROCESSO_DE_BAIXA`, `FINALIZADO` e `CANCELADO`.

Resultado esperado: `200 OK`, com o processo atualizado. A alteração também cria um registro em `historico_processo`.

## 6. Testes de validação e erro

### Campos obrigatórios

Envie, por exemplo, o seguinte body para criar usuário:

```json
{
	"nome": "",
	"email": "email-invalido",
	"senha": "123",
	"tipo": null
}
```

Resultado esperado: `400 Bad Request`, com um JSON contendo `status`, `message` e `timestamp`.

### Registro inexistente

Use um ID que não exista, por exemplo:

**GET** `{{baseUrl}}/api/processos-contemplacao/999999`

Resultado esperado: `404 Not Found`.

Formato esperado:

```json
{
	"status": 404,
	"message": "Processo não encontrado: 999999",
	"timestamp": "2026-09-12T10:30:00"
}
```

### Valores inválidos de enum

Envie um valor fora da lista, como `"tipo": "USUARIO"` ou `"status": "ABERTO"`. O Spring rejeita o JSON porque os valores precisam corresponder exatamente aos enums definidos no backend.

## 7. O que foi feito no backend

- Aplicação Spring Boot com nome `senai`, executando na porta `8080`.
- Configuração de conexão com MySQL por propriedades e variáveis de ambiente (`DB_URL`, `DB_USERNAME` e `DB_PASSWORD`).
- Flyway habilitado para versionar o banco e criar o schema inicial.
- Entidades JPA para usuários, perfis, consórcios, grupos, cotas, contemplações, processos, históricos, documentos, parcelas, pagamentos, comunicações, notificações e auditoria.
- Controllers REST para `/api/usuarios`, `/api/consorcios`, `/api/cotas` e `/api/processos-contemplacao`.
- DTOs de entrada e saída para não expor diretamente as entidades nas respostas.
- Validação dos bodies com Jakarta Validation, incluindo campos obrigatórios, e-mail válido, senha com no mínimo seis caracteres e valor de crédito positivo.
- Serviços com regras de busca de registros relacionados e mensagens de recurso não encontrado.
- Atualização de status de processo com gravação do histórico, usuário responsável e data de atualização.
- Tratamento global para erros de validação (`400`) e recursos inexistentes (`404`).
- Login com senha comparada por BCrypt, access token JWT e refresh token com hash SHA-256 no banco.
- Rotação e revogação de refresh tokens por `/api/auth/refresh` e `/api/auth/logout`.
- Configuração OpenAPI com esquema `bearerAuth` para o botão `Authorize` do Swagger.
- Os serviços de criação usam construtor vazio e setters, evitando falhas quando o Eclipse não atualiza o processamento de anotações Lombok.
- Os parâmetros de rota do processo são declarados explicitamente (`@PathVariable("id")`), garantindo que as consultas retornem `404` em vez de erro `500`.

## 8. Resumo dos endpoints disponíveis

| Método | Endpoint | Finalidade | Sucesso |
|---|---|---|---|
| `POST` | `/api/auth/login` | Gerar access e refresh tokens | `200` |
| `POST` | `/api/auth/refresh` | Renovar tokens | `200` |
| `POST` | `/api/auth/logout` | Revogar refresh token | `204` |
| `POST` | `/api/usuarios` | Criar usuário | `201` |
| `GET` | `/api/usuarios` | Listar usuários, exige JWT | `200` |
| `POST` | `/api/consorcios` | Criar consórcio, exige JWT | `201` |
| `GET` | `/api/consorcios` | Listar consórcios, exige JWT | `200` |
| `POST` | `/api/cotas` | Criar cota, exige JWT | `201` |
| `GET` | `/api/cotas` | Listar cotas, exige JWT | `200` |
| `GET` | `/api/processos-contemplacao/{id}` | Consultar processo, exige JWT | `200` |
| `PATCH` | `/api/processos-contemplacao/{id}/status` | Alterar status, exige JWT | `200` |

## 9. Integração de contemplados

Este fluxo recebe dados de outro sistema, cria ou localiza o cliente, cria a contemplação, cria o processo e tenta enviar a comunicação por e-mail.

### 9.1 Configurar Gmail

Não use a senha normal do Gmail. Na conta que enviará as mensagens:

1. Ative a verificação em duas etapas da conta Google.
2. Acesse **Conta Google > Segurança > Senhas de app**.
3. Crie uma senha de app para o sistema, por exemplo `Senai API`.
4. Copie os 16 caracteres gerados.
5. Configure as propriedades antes de iniciar a aplicação.

No Windows CMD:

```bat
set MAIL_ENABLED=true
set MAIL_USERNAME=seuemail@gmail.com
set MAIL_PASSWORD=senha-de-app-do-gmail
set INTEGRATION_API_KEY=integration-local-123
mvn spring-boot:run
```

Ou configure no `application.properties` local:

```properties
app.mail.enabled=true
spring.mail.host=smtp.gmail.com
spring.mail.port=465
spring.mail.username=seuemail@gmail.com
spring.mail.password=senha-de-app-do-gmail
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.socketFactory.port=465
spring.mail.properties.mail.smtp.socketFactory.class=javax.net.ssl.SSLSocketFactory
app.integration.api-key=integration-local-123
```

Não publique `spring.mail.password`, `JWT_SECRET` ou `app.integration.api-key` no Git. Em produção, use variáveis de ambiente ou um gerenciador de segredos.

Se `app.mail.enabled=false`, o processo será criado, mas a comunicação permanecerá `PENDENTE` com a mensagem de configuração no campo `erro`. Isso permite testar o recebimento sem enviar e-mails reais.

### 9.2 Configurar WhatsApp Cloud API

O WhatsApp usa a API oficial da Meta. No Meta for Developers:

1. Crie um App do tipo **Business**.
2. Adicione o produto **WhatsApp**.
3. Copie o **Phone Number ID** do número de teste ou do número comercial.
4. Gere um token de acesso com permissão `whatsapp_business_messaging`.
5. Para o número de teste, adicione o telefone destinatário na lista de números permitidos.

O telefone deve ser enviado no formato internacional, somente com números e código do país. Exemplo para Brasil: `5511999999999`.

No Windows CMD, antes de iniciar a aplicação:

```bat
set WHATSAPP_ENABLED=true
set WHATSAPP_PHONE_NUMBER_ID=ID_DO_PHONE_NUMBER
set WHATSAPP_ACCESS_TOKEN=TOKEN_DA_META
set WHATSAPP_API_VERSION=v23.0
mvn spring-boot:run
```

O token não deve ser colocado no Git ou no `application.properties`.

Em novas integrações, o backend tenta enviar a mesma mensagem pelos dois canais: e-mail e WhatsApp. A resposta mantém `comunicacaoId` e `statusComunicacao` para o e-mail e acrescenta `comunicacaoWhatsAppId` e `statusWhatsApp` para o WhatsApp.

Para enviar WhatsApp a um processo já existente:

**POST** `{{baseUrl}}/api/integracoes/processos/3/comunicacao/whatsapp`

Headers:

```text
Authorization: Bearer token-local-123
Content-Type: application/json
```

Body:

```json
{
	"telefone": "5511999999999",
	"mensagem": "Ola, sua contemplacao foi registrada. Acesse o atendimento para consultar os proximos passos."
}
```

Resultado esperado com a Meta configurada: `status: "SUCESSO"`.

### 9.3 Receber um contemplado de outro sistema

**POST** `{{baseUrl}}/api/integracoes/contemplacoes`

Headers:

```text
Content-Type: application/json
X-Integration-Key: integration-local-123
```

Body > raw > JSON:

```json
{
	"origem": "sistema-parceiro",
	"idExterno": "CONT-POSTMAN-001",
	"nome": "Maria da Silva",
	"email": "seu-email-de-teste@gmail.com",
	"telefone": "5511999999999",
	"cotaId": 1,
	"assembleiaId": 1,
	"dataContemplacao": "2026-09-15",
	"tipo": "SORTEIO",
	"valorCredito": 50000.00
}
```

O `cotaId` e o `assembleiaId` precisam existir no banco. Para consultar:

```sql
SELECT id, numero FROM cota;
SELECT id, data FROM assembleia;
```

Resposta esperada com Gmail habilitado:

```json
{
	"processoId": 2,
	"comunicacaoId": 1,
	"origem": "sistema-parceiro",
	"idExterno": "CONT-POSTMAN-001",
	"statusProcesso": "AGUARDANDO_COMUNICACAO",
	"statusComunicacao": "SUCESSO",
	"jaExistia": false
}
```

Guarde `processoId` e `comunicacaoId`. Eles serão usados para acompanhar o fluxo.

### 9.4 Evitar duplicidade

Envie novamente o mesmo body com o mesmo `origem` e `idExterno`. A API não criará outro processo. Ela responderá com `jaExistia: true` e os mesmos IDs.

O par abaixo identifica exclusivamente o recebimento:

```text
origem + idExterno
```

Sistemas diferentes podem usar o mesmo `idExterno`, desde que tenham `origem` diferente.

### 9.5 Consultar se foi notificado

**GET** `{{baseUrl}}/api/integracoes/processos/2/comunicacao`

Essa rota exige o header:

```text
Authorization: Bearer token-local-123
```

Resposta:

```json
{
	"id": 1,
	"processoId": 2,
	"canal": "EMAIL",
	"destinatario": "seu-email-de-teste@gmail.com",
	"status": "SUCESSO",
	"erro": null,
	"criadaEm": "2026-09-15T15:00:00",
	"enviadaEm": "2026-09-15T15:00:02"
}
```

Interpretação dos status:

- `PENDENTE`: ainda não enviado ou Gmail desabilitado.
- `SUCESSO`: o servidor SMTP aceitou o e-mail.
- `FALHA`: houve erro no envio; consulte `erro` e use o reenvio.

`SUCESSO` confirma o aceite pelo Gmail, mas não garante que o destinatário abriu a mensagem. Confirmações de entrega e leitura exigem integração adicional com o provedor.

### 9.6 Reenviar comunicação

**POST** `{{baseUrl}}/api/integracoes/processos/2/comunicacao/reenvio`

Use o token universal local ou um JWT de administrador:

```text
Authorization: Bearer token-local-123
```

O retorno mostra o novo status e eventual erro.

### 9.7 Dar baixa no processo

**POST** `{{baseUrl}}/api/integracoes/processos/2/baixa`

Headers:

```text
Content-Type: application/json
Authorization: Bearer token-local-123
```

Body:

```json
{
	"usuarioId": 1,
	"observacao": "Cliente comunicado e processo concluído."
}
```

Resposta esperada:

```json
{
	"id": 1,
	"processoId": 2,
	"usuarioId": 1,
	"data": "2026-09-15T15:20:00",
	"observacao": "Cliente comunicado e processo concluído."
}
```

A baixa altera o processo para `FINALIZADO`. Ela é idempotente: se for chamada novamente para o mesmo processo, retorna a baixa já existente.

### 9.8 Consultar se foi dada a baixa

**GET** `{{baseUrl}}/api/integracoes/processos/2/baixa`

Header:

```text
Authorization: Bearer token-local-123
```

Se houver baixa, retorna os dados dela. Se ainda não houver, a API retorna `404 Not Found`.

Também é possível consultar o processo:

**GET** `{{baseUrl}}/api/processos-contemplacao/2`

Depois da baixa, o campo `status` deve ser:

```text
FINALIZADO
```
