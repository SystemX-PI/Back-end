# Executar e validar o SystemX localmente

## Desenvolvimento

Mantenha `Back-end` e `Front-End` em diretórios irmãos. Na raiz do Back-end, prepare o `.env` conforme o [README](README.md#subir-o-projeto-completo-com-docker). Execute `docker compose --profile full up --build -d --wait --wait-timeout 600`. O frontend fica em 5173, a API em 8080, o MySQL em 3307 e a Evolution em 8081 por padrão, com acesso apenas local. O Nginx encaminha `/api` ao backend pela rede interna.

Para trabalhar sem containers, use Java 17, Maven, MySQL e o Vite. Configure `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` para um banco local, um `JWT_SECRET` próprio e a origem do Vite em `CORS_ALLOWED_ORIGINS`. No diretório `senai`, execute `mvn spring-boot:run`. No Front-End, use `npm ci` e `npm run dev`; configure `VITE_API_PROXY_TARGET` se a API local estiver em outra porta.

## Validação Docker em projeto descartável

Execute os comandos a partir da raiz do Back-end. O arquivo `compose.verify.yml` usa os Dockerfiles reais, expõe somente `127.0.0.1:5177` e possui volume independente por nome de projeto. Stripe, Evolution, Gmail, IA e pagamentos simulados ficam fora do ensaio. A API, o Nginx e o MySQL têm verificações de saúde. A porta 5177 precisa estar livre.

```sh
umask 077
scratch="$(mktemp -d)"
project="systemx-verify-$(openssl rand -hex 4)"
printf 'SYSTEMX_VERIFY_DB_PASSWORD=%s\nSYSTEMX_VERIFY_ROOT_PASSWORD=%s\nSYSTEMX_VERIFY_JWT_SECRET=%s\n' \
  "$(openssl rand -hex 24)" "$(openssl rand -hex 24)" "$(openssl rand -hex 48)" > "$scratch/compose.env"
openssl rand -hex 24 > "$scratch/test-pass"
compose() { docker compose --env-file "$scratch/compose.env" -p "$project" -f compose.verify.yml "$@"; }
compose config -q
compose up --build -d --wait --wait-timeout 600
hash="$(htpasswd -niBC 5 e2e < "$scratch/test-pass" | cut -d: -f2)"
{ printf "SET @test_password_hash = '%s';\n" "$hash"; cat ../Front-End/tests/fixtures/lote07.sql; } |
  compose exec -T db sh -ec 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"'
(cd ../Front-End && SYSTEMX_E2E_COMPOSE=1 SYSTEMX_E2E_PASSWORD="$(cat "$scratch/test-pass")" npm run test:ui:real)
```

Use `compose stop backend` para simular indisponibilidade. `compose up -d --wait` restaura a API e preserva o volume. O comando `compose down --volumes` é destinado apenas a esse projeto descartável após o ensaio. Remova também os arquivos efêmeros de senha. No Compose de desenvolvimento, `down` sem `--volumes` preserva os dados.

### Stripe Sandbox

No projeto descartável acima, use uma conta de teste e um preço recorrente mensal em BRL. Em outro terminal, inicie o listener com a Stripe CLI autenticada no mesmo sandbox:

```sh
stripe listen --forward-to http://127.0.0.1:5177/api/platform/webhooks/stripe \
  --events checkout.session.completed,checkout.session.async_payment_succeeded,checkout.session.async_payment_failed,checkout.session.expired,invoice.paid,invoice.payment_failed,customer.subscription.updated,customer.subscription.deleted
```

Em `"$scratch/stripe.env"`, configure `STRIPE_VERIFY_KEY`, `STRIPE_VERIFY_WEBHOOK_SECRET` e `STRIPE_VERIFY_MONTHLY_PRICE_ID`. Use o segredo gerado pelo listener e ajuste `STRIPE_VERIFY_MONTHLY_PRICE_CENTS` se o preço de teste diferir de 7990 centavos. Mantenha o arquivo privado e fora do Git. Inicie a pilha com os dois arquivos Compose:

```sh
compose_stripe() { docker compose --env-file "$scratch/compose.env" --env-file "$scratch/stripe.env" \
  -p "$project" -f compose.verify.yml -f compose.verify.stripe.yml "$@"; }
compose_stripe up --build -d --wait --wait-timeout 600
(cd ../Front-End && SYSTEMX_E2E_COMPOSE=1 SYSTEMX_E2E_STRIPE=1 \
  SYSTEMX_E2E_PASSWORD="$(cat "$scratch/test-pass")" npm run test:ui:real)
```

Forneça somente os números oficiais de teste da Stripe em `SYSTEMX_STRIPE_TEST_PAN` (aprovação) e `SYSTEMX_STRIPE_DECLINED_TEST_PAN` (recusa). Ao terminar, interrompa o listener e use `compose_stripe down --volumes` apenas no projeto descartável deste ensaio. Consulte a [Stripe CLI](https://docs.stripe.com/cli/sandbox) e os [dados oficiais de teste](https://docs.stripe.com/testing).

## Maven, Flyway e atualização

- `cd senai && mvn -B -ntp verify` roda a suíte padrão com H2.
- Para os testes MySQL, prepare três schemas descartáveis vazios em loopback, `systemx_lote03_test`, `systemx_flyway_fresh` e `systemx_flyway_upgrade`. Dê acesso exclusivo ao usuário de teste `systemx_lote03_test` e forneça a senha pelo ambiente `SYSTEMX_LOTE03_MYSQL_PASSWORD`.
- No diretório `senai`, execute `mvn -B -ntp -Dtest=ProcessoOperacaoIntegrationTest,StripeBillingIntegrationTest,FlywayMigrationIntegrationTest -Dsystemx.test.mysql.port=33307 -Dsystemx.test.flyway.port=33307 verify`, ajustando as duas portas para a instância descartável.
- Flyway aplica e valida V1–V21. A V2/V3 introduz dados legados de demonstração e a V7 vincula registros históricos. Confirme a política de dados antes de preparar um banco de entrega. V18 adiciona unicidade por operação. V19/V20 modifica a outbox. Pare os workers antigos antes de aplicar V20, preserve o histórico de migrações e confira reservas incertas. V21 adiciona a reserva de checkout sem reescrever assinaturas.
- Homologue a atualização em cópia isolada antes de aplicá-la em outro ambiente. Um banco descartável usa dados fictícios e não comprova o estado de um banco de produção.
- Para validar uma restauração, execute o teste `FlywayMigrationIntegrationTest#atualizacaoV19PreservaHistoricoEAssinaturaERecuperaSomenteDespachoSeguro` com `-Dsystemx.test.flyway.upgrade.phase=snapshot` para preparar `systemx_flyway_upgrade` na V19. Faça o backup desse schema fictício, restaure num quarto schema descartável vazio `systemx_flyway_restored` e execute o mesmo teste com `-Dsystemx.test.flyway.upgrade.phase=restore`.

Veja o [guia da API e dos exemplos Postman](senai/GUIA_TESTES_POSTMAN.md).
