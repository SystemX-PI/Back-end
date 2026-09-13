# Guia de testes no Postman

Este documento descreve como testar a API do backend do projeto pelo Postman e resume o que foi implementado.

## 1. Pré-requisitos

- Aplicação Spring Boot em execução na porta `8080`.
- MySQL em execução.
- Banco configurado conforme `src/main/resources/application.properties`:
	- banco: `senai`
	- usuário padrão: `root`
	- senha padrão: `root`
- O Flyway executa a migration `V1__create_schema.sql` na inicialização.
- As migrations `V2__seed_postman_data.sql` e `V3__seed_process_postman_data.sql` criam dados-base para os testes de consórcio, cota e processo.

Depois de alterar o código, pare a execução antiga no Eclipse e inicie novamente o projeto. Se a porta `8080` já estiver ocupada, não inicie uma segunda instância: use a aplicação que já está rodando ou encerre a execução anterior antes de iniciar outra.

No Postman, crie um ambiente com a variável:

```text
baseUrl = http://localhost:8080
```

Todas as requisições que enviam JSON devem usar o cabeçalho:

```text
Content-Type: application/json
```

Não há autenticação configurada nos endpoints atuais.

## 2. Ordem recomendada dos testes

1. Criar um usuário.
2. Listar usuários e guardar o `id` retornado.
3. Confirmar no banco um `tipo_consorcio` existente e guardar seu `id`.
4. Criar um consórcio usando esse `tipoId`.
5. Confirmar no banco um `grupo` existente e guardar seu `id`.
6. Criar uma cota usando esse `grupoId`.
7. Consultar um processo de contemplação existente.
8. Atualizar o status do processo usando o `id` do usuário criado.

Os endpoints para criar tipo de consórcio, grupo, contemplação e processo ainda não foram expostos. Por isso, esses registros precisam existir previamente no banco para os testes de consórcio, cota e processo.

## 3. Testes no Postman

### 3.1 Criar usuário

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

### 3.2 Listar usuários

**GET** `{{baseUrl}}/api/usuarios`

Resultado esperado: `200 OK`, com uma lista de usuários.

### 3.3 Criar consórcio

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

### 3.4 Listar consórcios

**GET** `{{baseUrl}}/api/consorcios`

Resultado esperado: `200 OK`, com uma lista de consórcios.

### 3.5 Criar cota

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

### 3.6 Listar cotas

**GET** `{{baseUrl}}/api/cotas`

Resultado esperado: `200 OK`, com uma lista de cotas.

### 3.7 Consultar processo de contemplação

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

### 3.8 Atualizar status do processo

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

## 4. Testes de validação e erro

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

## 5. O que foi feito no backend

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
- Os serviços de criação usam construtor vazio e setters, evitando falhas quando o Eclipse não atualiza o processamento de anotações Lombok.
- Os parâmetros de rota do processo são declarados explicitamente (`@PathVariable("id")`), garantindo que as consultas retornem `404` em vez de erro `500`.

## 6. Resumo dos endpoints disponíveis

| Método | Endpoint | Finalidade | Sucesso |
|---|---|---|---|
| `POST` | `/api/usuarios` | Criar usuário | `201` |
| `GET` | `/api/usuarios` | Listar usuários | `200` |
| `POST` | `/api/consorcios` | Criar consórcio | `201` |
| `GET` | `/api/consorcios` | Listar consórcios | `200` |
| `POST` | `/api/cotas` | Criar cota | `201` |
| `GET` | `/api/cotas` | Listar cotas | `200` |
| `GET` | `/api/processos-contemplacao/{id}` | Consultar processo | `200` |
| `PATCH` | `/api/processos-contemplacao/{id}/status` | Alterar status do processo | `200` |
