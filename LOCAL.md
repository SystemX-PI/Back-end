# Execucao local

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

O Dockerfile executa os 11 testes essenciais durante a construcao. Nao ha
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
