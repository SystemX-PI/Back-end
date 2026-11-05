# Relatório técnico — arquitetura MVC e integrações

## Visão geral

O projeto é uma aplicação web dividida em um front-end React/TypeScript e uma API Java 17 com Spring Boot. O front-end é uma SPA: apresenta as telas, coleta dados e chama endpoints REST que retornam JSON. A parte MVC fica principalmente na API; ela não renderiza as páginas React.

## Como o MVC está organizado

O caminho usual de uma requisição é:

```text
Navegador / React
      │ HTTP + JSON
      ▼
Controller ── valida DTOs e trata a requisição/resposta
      ▼
Service ───── aplica regras de negócio e coordena integrações
      ▼
Repository ── consulta e grava entidades no MySQL
      ▼
Entidades JPA / banco de dados
```

- **Model:** entidades JPA em `senai/src/main/java/com/systemX/senai/entity`, como `Usuario`, `PlatformAccount`, `PlatformCheckout`, `PlatformInstance` e `PlatformMailbox`. Representam os dados e seus relacionamentos.
- **View:** telas React em `Front-End/src/pages` e componentes em `Front-End/src/components`. A interface consome a API; não é uma view Thymeleaf do Spring.
- **Controller:** classes em `senai/src/main/java/com/systemX/senai/controller`, como `AuthController`, `PlatformController` e `IntegrationController`. Recebem HTTP, validam DTOs e chamam serviços.
- **Service:** classes em `senai/src/main/java/com/systemX/senai/service`, onde ficam regras e integrações, por exemplo `AuthService`, `StripeBillingService`, `PasswordResetService` e `EvolutionService`.
- **Repository:** interfaces Spring Data JPA em `senai/src/main/java/com/systemX/senai/repository`, usadas para persistência no MySQL.
- **DTOs:** tipos em `senai/src/main/java/com/systemX/senai/dto` definem o JSON de entrada e saída. Isso evita expor diretamente as entidades nas rotas.

## Assinaturas com Stripe

O front-end envia a solicitação de assinatura para o backend, que mantém as chaves privadas e fala com o Stripe:

1. O cliente escolhe o plano na área de assinatura.
2. `POST /api/platform/subscription/stripe-checkout` chama `StripeBillingService`, que cria uma sessão de Checkout hospedada pelo Stripe e devolve a URL.
3. O navegador abre o Checkout. Os dados do cartão são tratados pelo Stripe, não armazenados pelo formulário do sistema.
4. O Stripe chama `POST /api/platform/webhooks/stripe`. O backend verifica a assinatura do webhook e processa eventos de checkout, fatura e assinatura para atualizar a conta.
5. Para uma assinatura ativa, `POST /api/platform/subscription/portal` cria uma sessão do portal de cobrança do Stripe.

O estado de cliente e assinatura fica ligado à entidade `PlatformAccount`; os eventos recebidos são registrados para permitir tratamento idempotente. O modo de simulação local permanece disponível. A integração real só fica ativa quando `STRIPE_ENABLED`, chaves, ID do preço mensal e segredo do webhook estão configurados no `.env`; os valores secretos não devem ir ao Git. O retorno do navegador não substitui a confirmação do webhook.

## Recuperação de senha

O link **Esqueci minha senha** abre `Front-End/src/pages/ForgotPassword.tsx` e usa duas rotas públicas do `AuthController`:

- `POST /api/auth/forgot-password`: recebe o e-mail, cria um código numérico de oito dígitos e envia a mensagem pelo serviço SMTP.
- `POST /api/auth/reset-password`: recebe e-mail, código e nova senha; valida o código e atualiza a senha.

O backend guarda um HMAC do código, não o código em texto. O código expira em cinco minutos, tem até cinco tentativas e só pode ser usado uma vez. Solicitações são limitadas por endereço de origem, e a resposta não revela se um e-mail está cadastrado. Ao redefinir a senha, as sessões antigas são invalidadas.

O SMTP local usa Gmail e senha de aplicativo. `MAIL_ENABLED`, `MAIL_USERNAME` e `MAIL_PASSWORD` ficam no `Back-end/.env`, que é ignorado pelo Git. A configuração foi autenticada com o Gmail SMTP; nenhum e-mail de teste foi enviado. A senha de aplicativo deve ser mantida em segredo e rotacionada se for exposta.

## Evolution API / WhatsApp

O Docker Compose executa a Evolution API 2.3.7, com PostgreSQL e Redis próprios para os dados da integração. A API SystemX acessa a Evolution pela rede interna do Docker em `http://evolution:8080`; a chave de autenticação fica no `.env`. Os dados persistentes da Evolution são mantidos em volumes do Docker.

O fluxo passa pela interface de WhatsApp, pelo `PlatformController`/`PlatformService` e por `EvolutionService`, que chama a Evolution para criar ou localizar a instância, gerar QR Code, consultar status, desconectar ou remover a instância. A instância escolhida fica associada à conta da empresa. Para vincular um número, alguém ainda precisa escanear o QR Code pelo WhatsApp; iniciar os containers não pareia o telefone sozinho.

## Login com Google

O front-end usa Google Identity Services para obter o ID token. `AuthController` recebe esse token em `/api/auth/google`, e `GoogleIdentityService` valida assinatura, audiência, emissor, validade e e-mail verificado. O vínculo da conta é pelo identificador estável do Google (`sub`), e não apenas pelo e-mail.

Se a conta ainda não está cadastrada, o login abre o aviso **Criar conta**. O botão solicita o consentimento oficial do Google e, após a confirmação, pede o nome da empresa. O endpoint `/api/auth/google/register` cria a empresa e o titular, devolve tokens e a interface abre `/app` autenticada. O login não transforma silenciosamente uma identidade desconhecida em uma nova conta.

## Execução local e validações

Com Docker Desktop aberto, na pasta `Back-end`:

```powershell
docker compose --profile full up -d --wait --wait-timeout 600
```

O front-end fica em `http://localhost:5173`, a API/Swagger em `http://localhost:8080/swagger-ui/index.html` e o MySQL em `localhost:3307`. A última reconstrução do ambiente confirmou a compilação do front-end, a saúde dos containers e 25 testes Maven aprovados. O repositório não contém `.env`, senha de app, IP local nem dados de contas removidas.
