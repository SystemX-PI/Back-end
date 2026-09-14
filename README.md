# Back-end SystemX

API REST para gerenciamento de consorcios, cotas, usuarios e processos de contemplacao.

## Tecnologias

- Java 17
- Spring Boot 4.1.1
- Spring Web
- Spring Data JPA
- MySQL
- Flyway
- Gradle
- Swagger/OpenAPI

## Requisitos

- JDK 17
- MySQL 8
- Eclipse com suporte a Gradle/Buildship ou outro IDE compativel

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

Entre na pasta do modulo Gradle:

```powershell
cd senai
.\gradlew.bat clean bootRun
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

No Eclipse, importe especificamente a pasta `Back-end/senai` como `Gradle > Existing Gradle Project` e execute `SenaiApplication.java` como `Spring Boot App`.

## Testes

Para validar o projeto:

```powershell
cd senai
.\gradlew.bat clean test
```

O roteiro completo de testes no Postman esta em [senai/GUIA_TESTES_POSTMAN.md](senai/GUIA_TESTES_POSTMAN.md).

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
