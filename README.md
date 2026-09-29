# Back-end SystemX

API REST para gerenciamento de consorcios, cotas, usuarios e processos de contemplacao.

## Tecnologias

- Java 17
- Spring Boot 4.1.1
- Spring Web
- Spring Data JPA
- MySQL
- Flyway
- Maven
- Swagger/OpenAPI
- Integração de contemplados e comunicação por e-mail

## Requisitos

- JDK 17
- Docker Desktop com Docker Compose (recomendado para iniciar sem instalar Maven/MySQL)
- Para execucao manual: Maven 3.9+ e MySQL 8

## Inicializacao rapida com Docker

Depois de clonar o repositorio e iniciar o Docker Desktop, execute na raiz:

```powershell
docker compose up --build
```

O Compose aguarda o MySQL ficar saudavel, compila e testa o backend dentro do container e aplica as migrations do Flyway na primeira inicializacao. A API fica em `http://localhost:8080` e o Swagger em `http://localhost:8080/swagger-ui/index.html`.

Para parar, use `Ctrl+C` e depois `docker compose down`. Os dados do MySQL ficam no volume `senai-mysql-data`; para apagar tambem o banco local, use `docker compose down -v`.

Se as portas padrao ja estiverem ocupadas, defina `API_PORT` ou `MYSQL_PORT`, por exemplo:

```powershell
$env:API_PORT=8082
$env:MYSQL_PORT=3308
docker compose up --build
```

O token `token-local-123` e as credenciais do banco no Compose sao somente para desenvolvimento local. Nao os reutilize em producao.

## Configuracao do banco manual

Por padrao, a aplicacao usa:

```text
URL: jdbc:mysql://localhost:3306/senai
Usuario: root
Senha: root
```

Esses valores podem ser substituidos pelas variaveis de ambiente `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`.

O Flyway executa automaticamente as migrations em `senai/src/main/resources/db/migration` e cria os dados-base usados nos testes do Postman.

O envio de e-mail inicia desabilitado. Depois de autenticar, o frontend pode configurar o Gmail com senha de app por `PUT /api/configuracoes/email`:

```json
{
	"email": "remetente@gmail.com",
	"senhaAplicativo": "senha-de-app-do-google"
}
```

Envie o token JWT no header `Authorization: Bearer <token>`. A API responde com `configurado` e o e-mail, sem retornar a senha. As credenciais ficam somente na memoria do backend e precisam ser enviadas novamente quando ele reiniciar. Em producao, use HTTPS para proteger esse envio.

## Executar a aplicacao

Para executar sem Docker, entre na pasta do modulo Maven depois de configurar o MySQL:

```powershell
cd senai
mvn spring-boot:run
```

A API sera iniciada em:

```text
http://localhost:8080
```

Ao acessar `http://localhost:8080`, a aplicacao redireciona para a documentacao interativa da API:

```text
http://localhost:8080/
```

Na tela do Swagger, use **Try it out** para preencher os bodies e executar as requisicoes diretamente no navegador.

No Eclipse ou VS Code, importe a pasta `Back-end/senai` como projeto Maven e execute `SenaiApplication.java` como `Spring Boot App`.

## Testes

Para validar o projeto:

```powershell
cd senai
mvn test
```

O roteiro completo de testes no Postman esta em [senai/GUIA_TESTES_POSTMAN.md](senai/GUIA_TESTES_POSTMAN.md).

## Fluxo seguro de colaboracao

As branches seguem esta regra:

- `main`: branch de producao. Nao fazer push direto.
- `develop`: branch de integracao e validacao da equipe. As alteracoes devem chegar por Pull Request.
- `feature/<nome>` ou `fix/<nome>`: branch individual para cada tarefa.

### Como revisar o que outra pessoa fez

1. Confira o Pull Request e a branch de origem.
2. Revise a aba **Files changed** no GitHub.
3. Procure alteracoes em autenticacao, permissoes, banco, migrations, variaveis de ambiente e endpoints.
4. Execute os testes antes de aprovar:

```powershell
cd senai
mvn test
```

5. So aprove se a alteracao tiver objetivo claro, teste correspondente e nao expuser senha, token, segredo JWT ou credencial do banco.

### Como enviar uma alteracao para `develop`

```powershell
git checkout develop
git pull origin develop
git checkout -b feature/minha-alteracao

# editar e testar
git status
git diff
git add README.md senai
git commit -m "feat: descreve a alteracao"
git push -u origin feature/minha-alteracao
```

Depois, abra um Pull Request de `feature/minha-alteracao` para `develop`. A equipe revisa o diff, executa os testes e somente entao faz o merge. A `main` recebe apenas alteracoes ja validadas em `develop`.

### Regra de autorizacao

Cada pessoa pode alterar apenas o que foi combinado na tarefa. Nao aprovar automaticamente mudancas fora do escopo, exclusao de migrations, remocao de autenticacao, alteracao de permissoes ou inclusao de segredos. Para mudancas sensiveis, exigir pelo menos uma revisao de outra pessoa e registrar no Pull Request:

- o que foi alterado;
- por que foi alterado;
- como foi testado;
- quais endpoints e permissoes foram afetados.

O token universal de desenvolvimento existe apenas para testes locais. Ele deve permanecer desativado em ambientes compartilhados e nunca deve ser usado como credencial de producao.

## Endpoints principais

| Metodo | Endpoint | Finalidade |
|---|---|---|
| POST | `/api/usuarios` | Criar usuario |
| GET | `/api/usuarios` | Listar usuarios |
| POST | `/api/consorcios` | Criar consorcio |
| GET | `/api/consorcios` | Listar consorcios |
| POST | `/api/cotas` | Criar cota |
| GET | `/api/cotas` | Listar cotas |
| GET | `/api/processos-contemplacao/{id}` | Consultar processo |
| PATCH | `/api/processos-contemplacao/{id}/status` | Atualizar status do processo |
| POST | `/api/integracoes/contemplacoes` | Receber contemplado de sistema externo |
| GET | `/api/integracoes/processos/{id}/comunicacao` | Consultar envio da comunicação |
| POST | `/api/integracoes/processos/{id}/comunicacao/reenvio` | Reenviar comunicação |
| POST | `/api/integracoes/processos/{id}/baixa` | Finalizar processo com baixa |
| GET | `/api/integracoes/processos/{id}/baixa` | Consultar baixa |

O passo a passo completo dessa integração, configuração do Gmail e testes no Postman está em [senai/GUIA_TESTES_POSTMAN.md](senai/GUIA_TESTES_POSTMAN.md).
