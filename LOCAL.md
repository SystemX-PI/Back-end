# Execucao local

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

O primeiro build baixa o que estiver faltando. Os seguintes reutilizam imagens, camadas e caches npm/Maven; dependências só mudam quando os respectivos arquivos de versão mudam. Não use `--no-cache` nem limpe os volumes no fluxo normal. O build da API executa os testes de integração do backend; o frontend faz a compilação TypeScript/Vite, sem iniciar testes de navegador.

As imagens são construídas **na máquina de cada colega** a partir do Git atualizado. Fazer push no GitHub não publica automaticamente imagens em um registro Docker.

O cadastro público cria uma conta de cliente. Para criar um administrador local, copie `.env.example` para `.env` **somente se ainda não existir** e preencha `LOCAL_ADMIN_EMAIL` e `LOCAL_ADMIN_PASSWORD` (mínimo 10 caracteres). Reinicie o backend para aplicar. Senhas reais e `.env` não devem ser commitados. Recuperação por e-mail exige `MAIL_ENABLED`, `MAIL_USERNAME` e `MAIL_PASSWORD`; o restante funciona sem remetente configurado.

A configuração padrão permite a primeira subida sem `.env`; os valores de desenvolvimento não servem para produção. QR Code ainda exige confirmação no aparelho. O checkout simulado permanece disponível; o Stripe só é habilitado quando configurado explicitamente.

Para diagnosticar: `docker compose --profile full ps` e `docker compose --profile full logs --tail=80`. Se 5173 ou 8080 estiverem ocupadas por processos locais, pare esses processos antes de subir. O perfil `full` substitui a necessidade de iniciar `npm run dev` ou o Compose separado do frontend.


Na pasta Back-end, primeira execucao ou depois de mudar codigo:

```powershell
docker compose up --build -d
```

Para iniciar novamente sem mudar codigo (sem compilacao nem testes):

```powershell
docker compose up -d
```

Para parar, preservando dados:

```powershell
docker compose down
```

Swagger: http://localhost:8080/swagger-ui/index.html
O frontend usa /api pelo proxy do Vite para localhost:8080. Reinicie npm run dev
apos mudar configuracoes. A porta 8080 precisa estar livre.

O Dockerfile executa os 15 testes essenciais durante a construcao. Nao ha
skipTests. O cache do BuildKit guarda as dependencias Maven entre construcoes;
novas dependencias ainda precisam ser baixadas. Nao use --no-cache nem apague o
cache do Docker no fluxo normal. Sem mudancas, a camada validada e reutilizada.

O arquivo local ignorado docker-compose.override.yml limita API a 1 CPU/768 MB e
MySQL a 0,5 CPU/512 MB. A JVM usa ate 384 MB de heap. Os limites sao de execucao,
nao sao limites globais do Docker nem da compilacao. Para conferir o consumo:

```powershell
docker stats --no-stream
```

Para validar a configuracao sem subir os servicos:

```powershell
docker compose config --quiet
```

## Contas e configuracao segura

O cadastro público cria a conta de uma empresa e seu titular (CLIENTE). O titular cria os usuários de sua equipe em /api/platform/users. O administrador da plataforma gerencia contas de clientes em /api/platform/clients. Os endpoints /api/usuarios pertencem aos módulos legados.
Para um banco sem administrador, defina no PowerShell, na pasta Back-end:

```powershell
$env:LOCAL_ADMIN_EMAIL = "seu-email@exemplo.com"
$env:LOCAL_ADMIN_PASSWORD = Read-Host "Senha local (pelo menos 10 caracteres)"
docker compose up --build -d
Remove-Item Env:LOCAL_ADMIN_EMAIL
Remove-Item Env:LOCAL_ADMIN_PASSWORD
```

Isso cria a conta configurada como administradora apenas no perfil local. Se
ela ja existir como cliente ou funcionario, sera promovida uma vez. Remova essas variaveis ao terminar. Senhas em variaveis de ambiente
ficam visiveis para quem administra o Docker local. Nunca use credenciais reais de
outros ambientes. Administradores existentes continuam funcionando.

O token fixo fica desabilitado. Use /api/auth/login e o JWT retornado para o Swagger.
Fora do Compose, JWT_SECRET e obrigatorio e deve ter pelo menos 32 bytes. A chave
de integracao e configurada por INTEGRATION_API_KEY; o valor do Compose e so local.
Nao reutilize os segredos locais ao publicar.

A migration V7 aplica as novas colunas e indices sem apagar dados. Processos antigos
com comunicacao de e-mail sao ligados ao usuario pelo destinatario. Processos sem
esse vinculo permanecem sem cliente, evitando expor dados de outra pessoa.
Sessoes antigas precisam de renovacao/login apos atualizar o backend.

Frontend: abra /admin/cadastros para consorcios, grupos, cotas e tipos. Usuarios
sao geridos em /admin/usuarios; clientes, em /admin/clientes. Reinicie npm run dev.

## Administrador local configurado

As credenciais iniciais estao em .env, ignorado pelo Git. O Compose le esse arquivo
automaticamente: basta docker compose up --build -d. A conta configurada e criada
se estiver ausente; se ja existir como cliente/funcionario, e promovida uma vez e
as sessoes anteriores sao invalidadas. Contas ja administradoras nao tem a senha
redefinida em reinicializacoes. O comportamento so existe no perfil local.

## Evolution API junto com o Docker

`docker compose up -d` agora inicia tambem Evolution API 2.3.7, PostgreSQL e Redis.
Na primeira vez as imagens ausentes precisam ser baixadas; as proximas subidas
reutilizam essas imagens e os volumes. O backend aguarda os servicos ficarem saudaveis.

Acesse o frontend, entre como administrador e abra Integracoes > WhatsApp.
Use a instancia `consorcio`, clique em Gerar QR Code e escaneie em WhatsApp >
Aparelhos conectados > Conectar aparelho. O telefone ainda precisa autorizar a
conexao. Subir os containers nao conecta automaticamente um numero.

O backend usa `http://evolution:8080` dentro da rede Docker. A porta local da
Evolution e 8081, acessivel apenas nesta maquina. A API SystemX continua em 8080
e o MySQL do sistema em 3307. PostgreSQL e Redis da Evolution nao expoem portas
no Windows e nao substituem o banco senai.

EVOLUTION_API_KEY e compartilhada entre backend e Evolution. As chaves locais
foram geradas no .env ignorado pelo Git; o Compose tambem possui valores-padrao
somente de desenvolvimento para uma primeira subida sem configuracao adicional.
Nao reutilize esses padroes ao expor o ambiente. O .env.example lista as opcoes.

Os dados de instancia, PostgreSQL e Redis ficam em volumes persistentes. Nao use
`docker compose down -v` se quiser preservar dados e pareamentos.
A configuracao reduz a persistencia de mensagens/contatos/historico da Evolution;
os dados de instancia permanecem salvos para reutilizar a sessao.

## Assinaturas mensais com Stripe (modo de teste)

O checkout simulado continua preservado. Para testar o Stripe, crie no Dashboard um
produto Profissional e um preço recorrente mensal em BRL. O valor em centavos em
`STRIPE_MONTHLY_PRICE_CENTS` precisa ser igual ao preço do Dashboard; o backend
confere preço, moeda e intervalo antes de criar a sessão. O Stripe usa Checkout
hospedado para os dados do cartão e o portal do cliente para gerenciar/cancelar.

Configure no `Back-end/.env` local, sem copiar segredos para o frontend ou Git:

```dotenv
STRIPE_ENABLED=true
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_MONTHLY_PRICE_ID=price_...
STRIPE_MONTHLY_PRICE_CENTS=9990
```

`9990` acima é apenas exemplo e não define o preço comercial. Use o valor que foi
decidido e cadastrado no Dashboard. Mantenha `sk_test_` durante os testes. Para
webhooks locais, instale/autentique o Stripe CLI e execute:

```powershell
stripe listen --forward-to localhost:8080/api/platform/webhooks/stripe
```

Copie o `whsec_...` mostrado pelo CLI para `STRIPE_WEBHOOK_SECRET`. Em produção,
configure no Dashboard o endpoint público `/api/platform/webhooks/stripe` para
`checkout.session.completed`, `invoice.paid`, `invoice.payment_failed`,
`customer.subscription.updated` e `customer.subscription.deleted`. Para habilitar
cancelamento pelo portal, configure também as opções do Customer Portal no
Dashboard. Reinicie/reconstrua o backend depois de atualizar `.env`.

`invoice.paid` é a confirmação que libera ou renova o plano. Retorno do navegador
não ativa assinatura. O modo simulado não inicia cobranças e as contas existentes
não são inscritas automaticamente. Só troque para chaves `sk_live_` depois de
validar checkout, webhooks, renovação, falha e cancelamento em modo de teste.

Limites locais adicionais: Evolution 1 CPU/768 MB, PostgreSQL 0,5 CPU/256 MB,
Redis 0,25 CPU/128 MB. O consumo real pode ser menor; as primeiras migrations
podem usar mais CPU e demorar. Consulte `docker stats --no-stream`.

Comandos de diagnostico:

```powershell
docker compose ps
docker compose logs --tail=60 evolution
```

Referencia da composicao do fornecedor:
https://github.com/evolution-foundation/evolution-api/blob/2.3.7/docker-compose.yaml

## Gmail para recuperação de senha

Configure um remetente da plataforma no arquivo local ignorado `Back-end/.env`:

```dotenv
MAIL_ENABLED=true
MAIL_USERNAME=seu-remetente@gmail.com
MAIL_PASSWORD=sua-senha-de-aplicativo-do-google
```

Use uma senha de aplicativo do Google. Não coloque as credenciais no frontend nem no Git. As contas Gmail cadastradas pelos clientes não são usadas automaticamente para recuperar senhas da plataforma.

Na pasta Back-end, aplique a configuração sem recompilar:

```powershell
docker compose up -d --no-deps backend
```

No login, escolha “Esqueci minha senha”. O código recebido por e-mail expira em cinco minutos, aceita no máximo cinco tentativas e não pode ser reutilizado. Sem remetente configurado, a tela informa a indisponibilidade do envio.
