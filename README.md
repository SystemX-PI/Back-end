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

## Requisitos

- JDK 17
- MySQL 8
- Eclipse/VS Code com suporte a Maven

## Configuracao do banco

Por padrao, a aplicacao usa:

```text
URL: jdbc:mysql://localhost:3306/senai
Usuario: root
Senha: root
```

Esses valores podem ser substituidos pelas variaveis de ambiente `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`.

O Flyway executa automaticamente as migrations em `senai/src/main/resources/db/migration` e cria os dados-base usados nos testes do Postman.

## Executar a aplicacao

Entre na pasta do modulo Maven:

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
