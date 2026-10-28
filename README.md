# Back-end SystemX

API REST da plataforma por contas de clientes: usuários da equipe, identidade da marca, múltiplas integrações WhatsApp/Gmail, teste gratuito e compra simulada. Os módulos anteriores de consórcios permanecem disponíveis no código.

O cadastro e a demonstração incluem um WhatsApp e um Gmail. Somente a compra confirmada libera três WhatsApps e dois Gmails por 30 dias. Pix e cartão são simulados, com preço ilustrativo e sem cobrança real. O servidor valida o isolamento das contas e os limites.

Consulte [o relatório completo da plataforma](RELATORIO_PLATAFORMA.md) e [as instruções locais](LOCAL.md). Os endpoints atuais ficam em `/api/platform`; o cadastro e o login ficam em `/api/auth`. As seções de consórcios abaixo documentam os módulos legados.

## Subir o projeto completo com Docker

Requer apenas Docker Desktop com Compose v2; Node, Java e Maven não precisam estar instalados na máquina.
Clone os dois repositórios na mesma pasta, com estes nomes:

```text
projeto/
  Back-end/
  Front-End/
```

```powershell
git clone https://github.com/SystemX-PI/Back-end.git
git clone https://github.com/SystemX-PI/Front-End.git
cd Back-end
docker compose --profile full up --build -d --wait --wait-timeout 600
```

Ou, na pasta `Back-end`, execute `./start.ps1` no PowerShell. O script também funciona quando chamado de outra pasta.
No Windows, se a política local bloquear scripts, use o comando Docker acima.

- Site: http://localhost:5173
- Backend e Swagger: http://localhost:8080/swagger-ui/index.html
- MySQL para DBeaver: localhost:3307, banco `senai`, usuário `root`, senha `root` (desenvolvimento).
- Evolution API: localhost:8081. O backend acessa a Evolution pela rede Docker.

O comando aguarda os serviços ficarem saudáveis. O frontend é compilado e servido pelo Nginx; `/api` é encaminhado internamente ao backend em 8080. Rotas como `/app/perfil` funcionam também ao atualizar a página. Não é necessário configurar CORS ou instalar dependências no computador.

Para receber mudanças, atualize **os dois repositórios** e reconstrua:

```powershell
# Partindo da pasta Back-end
git pull --ff-only
git -C ../Front-End pull --ff-only
docker compose --profile full up --build -d --wait --wait-timeout 600
```

Para apenas ligar novamente, sem mudança no código, use `docker compose --profile full up -d --wait --wait-timeout 600`. Para parar, `docker compose --profile full down` preserva os volumes e os dados.

O primeiro build baixa o que estiver faltando. Os seguintes reutilizam imagens, camadas e caches npm/Maven; dependências só mudam quando os respectivos arquivos de versão mudam. Não use `--no-cache` nem limpe os volumes no fluxo normal. O build da API mantém os 15 testes existentes; o frontend faz a compilação TypeScript/Vite, sem iniciar testes de navegador.

As imagens são construídas **na máquina de cada colega** a partir do Git atualizado. Fazer push no GitHub não publica automaticamente imagens em um registro Docker.

O cadastro público cria uma conta de cliente. Para criar um administrador local, copie `.env.example` para `.env` **somente se ainda não existir** e preencha `LOCAL_ADMIN_EMAIL` e `LOCAL_ADMIN_PASSWORD` (mínimo 10 caracteres). Reinicie o backend para aplicar. Senhas reais e `.env` não devem ser commitados. Recuperação por e-mail exige `MAIL_ENABLED`, `MAIL_USERNAME` e `MAIL_PASSWORD`; o restante funciona sem remetente configurado.

A configuração padrão permite a primeira subida sem `.env`; os valores de desenvolvimento não servem para produção. QR Code ainda exige confirmação no aparelho. Pagamentos permanecem simulados.

Para diagnosticar: `docker compose --profile full ps` e `docker compose --profile full logs --tail=80`. Se 5173 ou 8080 estiverem ocupadas por processos locais, pare esses processos antes de subir. O perfil `full` substitui a necessidade de iniciar `npm run dev` ou o Compose separado do frontend.

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

Depois de clonar o repositorio e iniciar o Docker Desktop, execute na pasta `Back-end`:

```powershell
docker compose up --build
```

O Compose aguarda o MySQL ficar saudavel, compila e testa o backend dentro do container e aplica as migrations do Flyway na primeira inicializacao. A API fica em `http://localhost:8080` e o Swagger em `http://localhost:8080/swagger-ui/index.html`.

Para parar, use `Ctrl+C` e depois `docker compose down`. Os dados do MySQL ficam no volume `senai-mysql-data`; para apagar tambem o banco local, use `docker compose down -v`.

O backend usa a porta 8080. O MySQL do Compose é publicado em 3307, a Evolution em 8081; os bancos e o Redis da Evolution usam a rede interna do Docker. O Compose inicia os serviços necessários automaticamente. Se 8080 estiver ocupada, libere essa porta antes de iniciar.

O Compose ativa o perfil `local`. O token fixo fica desabilitado; use JWT obtido no login. As credenciais do Compose sao apenas de desenvolvimento.

## Configuracao do banco manual

Por padrao, a aplicacao usa:

```text
URL: jdbc:mysql://localhost:3306/senai
Usuario: root
Senha: root
```

Esses valores podem ser substituidos pelas variaveis de ambiente `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`.

O Flyway executa automaticamente as migrations em `senai/src/main/resources/db/migration` e cria os dados-base usados nos testes do Postman.

## Login com Google

O botão usa Google Identity Services no navegador. O front-end envia o ID token recebido para `POST /api/auth/google`; o Spring verifica assinatura, emissor, validade e audiência com o `GOOGLE_CLIENT_ID` do servidor, além de exigir e-mail verificado. O usuário é identificado pelo `sub` único do Google. Senhas e tokens Google não são armazenados. A sessão da aplicação continua usando o JWT e o refresh token rotativo que já existem.

Se o `sub` já estiver cadastrado, a conta existente é reconhecida. Um novo Google cria um usuário `CLIENTE` e a conta de teste correspondente, sem privilégios administrativos. Se já houver uma conta local com o mesmo e-mail, o login Google não a associa automaticamente: entre com a senha local e, em **Meu perfil**, vincule o Google enquanto estiver autenticado. Essa operação exige a sessão atual e um ID token Google válido. O logout continua revogando o refresh token da aplicação.

### Google Cloud Console

1. Abra o [Google Cloud Console](https://console.cloud.google.com/) e crie ou selecione um projeto.
2. Em **Google Auth Platform → Branding**, informe o nome do app, o e-mail de suporte e os links da política de privacidade e da página inicial para produção. Configure o público interno ou externo conforme a equipe. Enquanto o app externo estiver em teste, adicione as contas de teste.
3. Em **Google Auth Platform → Clients**, crie um cliente OAuth do tipo **Web application**.
4. Em **Authorized JavaScript origins**, adicione `http://localhost`, `http://localhost:5173` e, para produção, a origem HTTPS exata do site (por exemplo `https://app.suaempresa.com`). Não use curingas. Este fluxo usa o modo popup, então não há URI de redirecionamento/callback.
5. Para autenticação, use apenas os escopos padrão `openid`, `email` e `profile`. Copie o **Client ID**. O Client ID é público e vai para front e back; não use nem publique o Client Secret.

Para produção, cadastre e verifique no Console o domínio que hospeda a página inicial e a política de privacidade, e informe esse domínio na configuração de domínios autorizados. No modo externo de teste, somente as contas de teste adicionadas poderão autenticar.

O Google muda os nomes e a navegação do Console ocasionalmente. Consulte também o [guia oficial de configuração do Google Identity Services](https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid).

### Configuração local

Para execução com Docker Compose, na raiz de `Back-end`, copie `.env.example` para `.env` sem sobrescrever um arquivo existente e preencha `GOOGLE_CLIENT_ID`. O Compose usa esse Client ID na compilação do front-end e na validação do back-end. Os arquivos `.env` não devem ser commitados.

Para executar front e back fora do Docker:

```powershell
# Front-End: crie .env a partir do exemplo somente se ele ainda não existir.
cd ../Front-End
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# Edite VITE_GOOGLE_CLIENT_ID no arquivo .env.
npm ci
npm run dev

# Em outro PowerShell, Back-end: configure o Client ID no arquivo local ignorado.
cd ../Back-end/senai
if (-not (Test-Path application-local.properties)) { Copy-Item application-local.properties.example application-local.properties }
# Edite app.auth.google.client-id no application-local.properties.
mvn spring-boot:run
```

Depois de iniciar os dois processos, abra `http://localhost:5173/login` e use o botão **Entrar com Google**. Um login novo cria a conta; um e-mail que já pertence a uma conta local pede para entrar com a senha e vincular o Google pelo perfil. O teste real depende de um Client ID válido e de uma conta Google autorizada no Console.

Para desenvolvimento com origens diferentes, ajuste `app.cors.allowed-origins` (execução manual) ou `CORS_ALLOWED_ORIGINS` (Compose) para uma lista separada por vírgulas de origens exatas, incluindo esquema e porta. Tokens são enviados no header `Authorization`, então o CORS não habilita credenciais/cookies. Com o Vite proxy ou o Nginx do Compose, o navegador acessa `/api` na mesma origem e o CORS não é necessário.

### Produção e diagnóstico

Use HTTPS no site e no endpoint da API. Cadastre a origem HTTPS real em **Authorized JavaScript origins** e inclua-a em `CORS_ALLOWED_ORIGINS` somente se o browser chamar uma API de outra origem. Configure o mesmo Client ID em `VITE_GOOGLE_CLIENT_ID` durante o build do front-end e em `GOOGLE_CLIENT_ID` no servidor. Mantenha `JWT_SECRET`, credenciais do banco e outros segredos somente no servidor.

- `VITE_GOOGLE_CLIENT_ID` ausente: o login Google fica desabilitado e a tela mostra como configurar o Client ID.
- Erro de origem: confira se esquema, domínio e porta do site aparecem exatamente nas origens JavaScript autorizadas.
- Token rejeitado: confirme que front e back usam o mesmo Client ID e que o relógio do servidor está correto.
- E-mail já cadastrado: entre com a senha local e vincule a conta Google em **Meu perfil**.

A migration `V13__google_auth_identity.sql` adiciona provedor, subject Google único e datas de criação/atualização ao usuário. As migrations são aplicadas automaticamente pelo Flyway na próxima inicialização do back-end; a conta Google nunca define o tipo de usuário enviado pelo navegador.

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

## CRUD e permissoes atuais

- Cadastro publico: `POST /api/auth/register` recebe `nome`, `email`, `senha` e cria somente CLIENTE.
- `POST /api/usuarios` cria contas administrativas ou clientes e exige ADMINISTRADOR.
- Usuarios: somente ADMINISTRADOR consulta, cria, edita, troca senha ou exclui.
- Consorcios, grupos, cotas e tipos: ADMINISTRADOR altera; FUNCIONARIO pode consultar.
- Clientes: ADMINISTRADOR/FUNCIONARIO consultam `GET /api/clientes/pagina`.
- Portal: CLIENTE consulta somente seus processos em `GET /api/portal/processos` e `GET /api/portal/processos/{id}`.

Os cinco cadastros oferecem POST/GET na raiz e GET/PUT/DELETE em `/{id}`.
Para listar sem baixar toda a tabela, use `GET /api/{recurso}/pagina?page=0&size=20&q=busca`.
A resposta e `{items,total,page,size}`, com tamanho de 1 a 100 e pagina iniciando em zero.
Listas legadas na raiz retornam no maximo os 100 registros mais recentes.

PUT de usuario recebe `nome`, `email`, `tipo`, `ativo`, sem senha.
Para trocar a senha, use `PUT /api/usuarios/{id}/senha` com `{ "senha": "nova-senha" }`.
Mudancas de e-mail, perfil, estado ativo ou senha invalidam sessoes anteriores.
O ultimo administrador ativo nao pode ser removido ou desativado.

Grupos recebem `numero`, `consorcioId`, `quantidadeCotas` (opcional, inteiro positivo)
e `valorParcela` (opcional, positivo). Tipos recebem `nome`.
Crie tipo, consorcio, grupo e cota nessa ordem. Exclua dependencias antes dos pais.
IDs inexistentes retornam 404; entrada invalida, 400; duplicidade/vinculos, 409.

A fila paginada usa `GET /api/integracoes/comunicacoes/fila/pagina?page=0&size=20&q=&status=`.
Filtros: PENDENTE, FALHA, COMUNICADO_PARCIAL ou COMUNICADO_NOS_DOIS.
As comunicacoes mais recentes dos processos da pagina sao buscadas em lote.

Veja [LOCAL.md](LOCAL.md) para iniciar, reutilizar a imagem e configurar o primeiro administrador.

## WhatsApp integrado na inicializacao

O Compose inicia a Evolution API e suas dependencias com `docker compose up -d`.
O backend recebe URL/chave internamente e aguarda a Evolution ficar saudavel.
Depois do login administrativo, gere o QR em Integracoes > WhatsApp e escaneie no
telefone. Veja [LOCAL.md](LOCAL.md) para portas, persistencia e limites locais.
