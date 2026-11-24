# Login com Google — ambiente local

## Endereços

- Aplicação: http://localhost:5173/login
- Cadastro: http://localhost:5173/register
- API/Swagger: http://localhost:8080/swagger-ui/index.html
- MySQL para DBeaver: `localhost:3307`, banco `senai` (credenciais locais documentadas em `Back-end/LOCAL.md`).

### Acesso de outro computador na mesma rede Wi-Fi

Descubra o IPv4 do adaptador Wi-Fi com `ipconfig` e, no outro computador conectado à mesma rede, abra `http://<IPv4-do-PC>:5173/login`. O endereço pode mudar ao reconectar. O PC que hospeda precisa permanecer ligado, com Docker Desktop e os containers em execução.

Para habilitar a API nesse endereço, configure `CORS_ALLOWED_ORIGINS` em `Back-end/.env` com as origens `http://localhost:5173` e `http://<IPv4-do-PC>:5173`. Esse arquivo é local e ignorado pelo Git. O botão Google pode ser bloqueado pelo Google ao usar um IP privado como origem OAuth; para esse endereço, use login por e-mail e senha. Se o Windows ou a rede Wi-Fi bloquear a conexão, permita a porta TCP 5173 no Firewall para a rede local; redes corporativas/escolares também podem isolar os dispositivos entre si.

## Iniciar ou reiniciar

Abra o Docker Desktop. No PowerShell, execute:

```powershell
cd "C:\Projetos\SystemX\Back-end"
docker compose --profile full up -d --wait --wait-timeout 600
```

Abra `http://localhost:5173/login` e atualize a página. Para reconstruir depois de alterações no código:

```powershell
docker compose --profile full up --build -d --wait --wait-timeout 600
```

O primeiro comando reaproveita as imagens locais; o segundo reconstrói a API e o site.

## Configuração do Google Cloud

O projeto usa o mesmo OAuth Client ID no site (`Front-End/.env`, `VITE_GOOGLE_CLIENT_ID`) e na API (`Back-end/.env`, `GOOGLE_CLIENT_ID`). Esses arquivos são locais e não devem ser publicados no Git.

No OAuth Client ID do tipo **Aplicativo da Web**, confirme:

- Origem JavaScript autorizada: `http://localhost:5173`.
- A tela de consentimento está publicada ou, em modo **Testing**, o Gmail usado no teste está incluído em **Test users**.
- O OAuth Client ID configurado é o mesmo nos dois arquivos `.env`.

O fluxo local usa a janela oficial de autorização do Google (popup) e não exige URI de redirecionamento OAuth. O consentimento é solicitado ao clicar em **Criar conta**. A biblioteca Google Identity Services usa diálogo para esse fluxo; o navegador pode mostrar uma janela popup, em vez de uma aba comum. Para produção, configure a origem HTTPS real e a política de consentimento apropriada.

## Entrar e criar conta

1. Clique em **Fazer Login com o Google** e selecione a conta.
2. Se ela já foi cadastrada com Google e continua ativa, a API valida o ID token, localiza o vínculo seguro pelo identificador Google (`sub`) e abre `/app`.
3. Se não existir cadastro, a tela oferece **Criar conta**. Ao continuar, abre a janela oficial de consentimento do Google; depois informe o nome da empresa. O cadastro cria a conta e já inicia a sessão na página inicial (`/app`).
4. Se já existir uma conta com o mesmo e-mail, mas sem vínculo Google, entre com senha e vincule o Google pelo perfil. O sistema não associa contas automaticamente apenas pelo e-mail.

Contas apagadas anteriormente precisam ser cadastradas novamente antes de entrar. Uma conta Google fora da lista de **Test users** pode ser bloqueada pelo próprio Google antes de chegar à aplicação.

O link **Esqueci minha senha** abre a recuperação por código. Para o envio funcionar, configure em `Back-end/.env` `MAIL_ENABLED=true`, `MAIL_USERNAME` com o Gmail remetente e `MAIL_PASSWORD` com uma senha de aplicativo do Google (não use a senha normal). Sem essas credenciais, o formulário informa que a recuperação por e-mail está indisponível. Consulte a seção “Gmail para recuperação de senha” em `Back-end/LOCAL.md`.

## Diagnóstico rápido

- Erro de conexão: veja se os serviços estão saudáveis com `docker compose --profile full ps`.
- Mensagem de autenticação inválida: atualize a tela e selecione a conta novamente; confirme que os Client IDs coincidem.
- Conta ainda não cadastrada: use **Criar conta** no aviso.
- E-mail já usado sem vínculo Google: entre com senha e faça o vínculo nas configurações do perfil.
- Google bloqueou a conta: em modo Testing, inclua esse Gmail em **Test users** no Google Auth Platform.
- Para obter o motivo técnico do backend, consulte `docker compose logs --since 10m backend`. Os registros de login não gravam token, e-mail ou identificador da conta.

## Estado desta alteração

O tratamento de erros do login Google fica visível logo abaixo do botão. O backend agora diferencia token malformado, audiência/emissor/horário inválidos e indisponibilidade de validação nos logs, sem registrar credenciais ou dados pessoais. O commit ainda depende de autorização explícita do responsável.

