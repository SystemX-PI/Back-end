package com.systemX.senai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.systemX.senai.entity.PlatformAccount;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionTemplate;
import com.systemX.senai.enums.TipoUsuario;
import com.systemX.senai.repository.PlatformAccountRepository;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Price;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PriceRetrieveParams;
import com.stripe.param.SubscriptionRetrieveParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import com.stripe.param.checkout.SessionCreateParams;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StripeBillingService {
    private static final String STATUS_ACTIVE = "active";
    private static final String INTERVAL_MONTH = "month";
    private static final String METADATA_PLATFORM_ACCOUNT_ID = "platform_account_id";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_SUBSCRIPTION = "subscription";
    private static final String FIELD_CUSTOMER = "customer";
    private final PlatformService platform;
    private final PlatformAccountRepository accounts;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final EntityManager entityManager;
    private final boolean liveMode;
    private final ObjectMapper json = new ObjectMapper();
    private final StripeClient stripe;
    private final boolean enabled;
    private final String webhookSecret;
    private final String priceId;
    private final int monthlyPriceCents;
    private final String successUrl;
    private final String cancelUrl;

    public StripeBillingService(PlatformService platform, PlatformAccountRepository accounts,
            JdbcTemplate jdbc, PlatformTransactionManager transactionManager, EntityManager entityManager,
            @Value("${app.billing.stripe.enabled:false}") boolean enabled,
            @Value("${app.billing.stripe.secret-key:}") String secretKey,
            @Value("${app.billing.stripe.webhook-secret:}") String webhookSecret,
            @Value("${app.billing.stripe.monthly-price-id:}") String priceId,
            @Value("${app.billing.stripe.monthly-price-cents:0}") int monthlyPriceCents,
            @Value("${app.billing.stripe.success-url:http://localhost:5173/app/assinatura?stripe=success}") String successUrl,
            @Value("${app.billing.stripe.cancel-url:http://localhost:5173/app/upgrade?stripe=cancelled}") String cancelUrl) {
        this.platform = platform;
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.entityManager = entityManager;
        this.liveMode = secretKey.startsWith("sk_live_") || secretKey.startsWith("rk_live_");
        this.enabled = enabled && !secretKey.isBlank() && !webhookSecret.isBlank()
                && !priceId.isBlank() && monthlyPriceCents > 0;
        this.stripe = this.enabled ? new StripeClient(secretKey) : null;
        this.webhookSecret = webhookSecret;
        this.priceId = priceId;
        this.monthlyPriceCents = monthlyPriceCents;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
    }

    private record CheckoutReservation(String attempt, Map<String, Object> existing) {}

    public Map<String, Object> createCheckout(Long requestedAccountId, boolean pix) {
        if (!enabled) throw error(503, "Checkout mensal ainda não configurado.");
        CheckoutReservation reservation = transactions.execute(tx -> reserveCheckout(requestedAccountId, pix));
        if (reservation.existing() != null) return reservation.existing();
        return transactions.execute(tx -> createReservedCheckout(requestedAccountId, pix, reservation.attempt()));
    }

    private CheckoutReservation reserveCheckout(Long requestedAccountId, boolean pix) {
        var account = lockCheckoutAccount(requestedAccountId);
        var existing = existingCheckout(account, pix);
        if (existing != null) return new CheckoutReservation(null, existing);
        String method = pix ? "PIX" : "CARD";
        if (account.getStripeCheckoutAttempt() == null) {
            account.setStripeCheckoutAttempt(UUID.randomUUID().toString());
            account.setStripeCheckoutAttemptMethod(method);
            account.setStripeCheckoutAttemptAt(Instant.now());
        } else if (!method.equals(account.getStripeCheckoutAttemptMethod())
                || account.getStripeCheckoutAttemptAt().isBefore(Instant.now().minus(23, ChronoUnit.HOURS))) {
            throw error(409, "Existe um checkout a conciliar. Consulte a assinatura antes de tentar novamente.");
        }
        return new CheckoutReservation(account.getStripeCheckoutAttempt(), null);
    }

    private PlatformAccount lockCheckoutAccount(Long requestedAccountId) {
        var user = platform.currentUser();
        if (user.getTipo() != TipoUsuario.CLIENTE && user.getTipo() != TipoUsuario.ADMINISTRADOR)
            throw error(403, "Somente o titular pode contratar um plano.");
        var account = lockBillingAccount(requestedAccountId);
        if (account.isSuspended()) throw error(403, "Conta suspensa.");
        if (account.isProfessional() && account.getPaidUntil() != null && account.getPaidUntil().isAfter(Instant.now()))
            throw error(409, "A assinatura Profissional já está ativa.");
        if (account.getStripeSubscriptionId() != null && !"canceled".equals(account.getStripeSubscriptionStatus())
                && !"incomplete_expired".equals(account.getStripeSubscriptionStatus()))
            throw error(409, "Já existe uma assinatura para esta conta. Gerencie a assinatura no Stripe.");
        return account;
    }

    private PlatformAccount lockBillingAccount(Long requestedAccountId) {
        var authorized = platform.account(requestedAccountId);
        Long accountId = authorized.getId();
        // A autorização pode carregar a conta antes de outra transação liberar o bloqueio.
        entityManager.detach(authorized);
        return accounts.lockById(accountId).orElseThrow(() -> error(404, "Conta não encontrada."));
    }

    private Map<String, Object> existingCheckout(PlatformAccount account, boolean pix) {
        try {
            if (account.getStripePixSessionId() != null) {
                var session = retrievePix(account.getStripePixSessionId());
                verifySession(account, session, account.getStripePixSessionId(), "payment");
                if ("open".equals(session.getStatus()) && pix) return checkoutView(session.getUrl(), true);
                if (!pixClosedWithoutPayment(session))
                    throw error(409, "Já existe um Pix em andamento. Consulte a assinatura.");
                clearPix(account);
            }
            if (account.getStripeCheckoutSessionId() != null) {
                var session = stripe.v1().checkout().sessions().retrieve(account.getStripeCheckoutSessionId());
                verifySession(account, session, account.getStripeCheckoutSessionId(), FIELD_SUBSCRIPTION);
                if ("open".equals(session.getStatus()) && !pix) return checkoutView(session.getUrl(), false);
                if (!"expired".equals(session.getStatus()) || "paid".equals(session.getPaymentStatus()))
                    throw error(409, "Já existe um checkout por cartão em andamento. Consulte a assinatura.");
                clearCard(account);
            }
            return null;
        } catch (StripeException ex) {
            throw error(502, "Não foi possível verificar o checkout existente no Stripe.");
        }
    }

    private void verifySession(PlatformAccount account, Session session, String sessionId, String mode) {
        if (sessionId == null || !sessionId.equals(session.getId()) || !mode.equals(session.getMode())
                || !account.getId().toString().equals(session.getClientReferenceId())
                || !sameCustomer(account, session.getCustomer()))
            throw error(409, "O checkout Stripe não corresponde a esta conta.");
    }

    private Map<String, Object> createReservedCheckout(Long requestedAccountId, boolean pix, String attempt) {
        var account = lockCheckoutAccount(requestedAccountId);
        if (!attempt.equals(account.getStripeCheckoutAttempt())) {
            var existing = existingCheckout(account, pix);
            if (existing != null) return existing;
            throw error(409, "O checkout foi atualizado. Consulte a assinatura.");
        }
        if (!account.getStripeCheckoutAttemptAt().isAfter(Instant.now().minus(23, ChronoUnit.HOURS)))
            throw error(409, "Existe um checkout a conciliar. Consulte a assinatura.");
        try {
            Price price = stripe.v1().prices().retrieve(priceId, PriceRetrieveParams.builder().build());
            if (!Boolean.TRUE.equals(price.getActive()) || price.getUnitAmount() == null || price.getUnitAmount() != monthlyPriceCents
                    || !"brl".equalsIgnoreCase(price.getCurrency()) || price.getRecurring() == null
                    || !INTERVAL_MONTH.equals(price.getRecurring().getInterval())
                    || !Long.valueOf(1).equals(price.getRecurring().getIntervalCount()))
                throw error(503, "O preço Stripe configurado não corresponde a uma assinatura mensal em BRL.");

            String customerId = account.getStripeCustomerId();
            if (customerId == null || customerId.isBlank()) {
                var customerParams = CustomerCreateParams.builder()
                        .setEmail(account.getOwnerEmail())
                        .putMetadata(METADATA_PLATFORM_ACCOUNT_ID, account.getId().toString())
                        .build();
                Customer customer = stripe.v1().customers().create(customerParams,
                        RequestOptions.builder().setIdempotencyKey("systemx-customer-" + account.getId()).build());
                customerId = customer.getId();
                account.setStripeCustomerId(customerId);
                accounts.save(account);
            }
            var subscriptionData = SessionCreateParams.SubscriptionData.builder()
                    .putMetadata(METADATA_PLATFORM_ACCOUNT_ID, account.getId().toString()).build();
            var params = SessionCreateParams.builder()
                    .setCustomer(customerId)
                    .setClientReferenceId(account.getId().toString())
                    .setSuccessUrl(accountUrl(successUrl, requestedAccountId))
                    .setCancelUrl(accountUrl(cancelUrl, requestedAccountId));
            if (pix) {
                var product = SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName("SystemX Profissional · 30 dias").build();
                var priceData = SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency("brl").setUnitAmount((long) monthlyPriceCents).setProductData(product).build();
                params.setMode(SessionCreateParams.Mode.PAYMENT)
                        .putExtraParam("payment_method_types", List.of("pix"))
                        .addLineItem(SessionCreateParams.LineItem.builder().setPriceData(priceData).setQuantity(1L).build());
            } else {
                params.setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                        .putExtraParam("payment_method_types", List.of("card"))
                        .setSubscriptionData(subscriptionData)
                        .addLineItem(SessionCreateParams.LineItem.builder().setPrice(priceId).setQuantity(1L).build());
            }
            Session session = stripe.v1().checkout().sessions().create(params.build(),
                    RequestOptions.builder().setIdempotencyKey("systemx-checkout-" + account.getId() + "-" + attempt).build());
            if (session.getId() == null || session.getUrl() == null || session.getExpiresAt() == null)
                throw error(502, "O Stripe não confirmou a sessão de checkout.");
            verifySession(account, session, session.getId(), pix ? "payment" : FIELD_SUBSCRIPTION);
            account.setStripeCheckoutAttempt(null);
            account.setStripeCheckoutAttemptMethod(null);
            account.setStripeCheckoutAttemptAt(null);
            account.setStripeSubscriptionId(null);
            account.setStripeSubscriptionStatus(null);
            account.setStripeSubscriptionEventAt(null);
            if (pix) {
                account.setStripePixSessionId(session.getId());
                account.setStripePixAmountCents(monthlyPriceCents);
            } else {
                account.setStripeCheckoutSessionId(session.getId());
                account.setStripeCheckoutExpiresAt(Instant.ofEpochSecond(session.getExpiresAt()));
            }
            accounts.save(account);
            return checkoutView(session.getUrl(), pix);
        } catch (StripeException ex) {
            throw error(502, pix ? "Não foi possível gerar o Pix. Verifique se o Pix está habilitado na conta Stripe."
                    : "Não foi possível iniciar o checkout no Stripe.");
        }
    }

    private String accountUrl(String url, Long accountId) {
        if (accountId == null) return url;
        return org.springframework.web.util.UriComponentsBuilder.fromUriString(url)
                .replaceQueryParam("account", accountId).build().toUriString();
    }

    private Map<String, Object> checkoutView(String url, boolean pix) {
        var response = new LinkedHashMap<String, Object>();
        response.put("checkoutUrl", url);
        response.put(FIELD_STATUS, "PENDING");
        response.put("amountCents", monthlyPriceCents);
        response.put("currency", "BRL");
        response.put("interval", pix ? "once" : INTERVAL_MONTH);
        response.put("simulation", false);
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> createPortalSession(Long requestedAccountId) {
        if (!enabled || stripe == null) throw error(503, "Portal Stripe ainda não configurado.");
        var user = platform.currentUser();
        if (user.getTipo() != TipoUsuario.CLIENTE && user.getTipo() != TipoUsuario.ADMINISTRADOR)
            throw error(403, "Somente o titular pode gerenciar a assinatura.");
        var account = platform.account(requestedAccountId);
        if (account.getStripeCustomerId() == null || account.getStripeSubscriptionId() == null)
            throw error(404, "Nenhuma assinatura Stripe foi encontrada para esta conta.");
        try {
            var params = com.stripe.param.billingportal.SessionCreateParams.builder()
                    .setCustomer(account.getStripeCustomerId())
                    .setReturnUrl(successUrl.substring(0, successUrl.indexOf('?') >= 0 ? successUrl.indexOf('?') : successUrl.length()))
                    .build();
            var session = stripe.v1().billingPortal().sessions().create(params);
            return Map.of("url", session.getUrl());
        } catch (StripeException ex) {
            throw error(502, "Não foi possível abrir o portal de assinatura do Stripe.");
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> refreshSubscription(Long requestedAccountId) {
        if (!enabled || stripe == null) throw error(503, "Consulta de assinatura Stripe ainda não configurada.");
        var user = platform.currentUser();
        if (user.getTipo() != TipoUsuario.CLIENTE && user.getTipo() != TipoUsuario.ADMINISTRADOR)
            throw error(403, "Somente o titular pode consultar a assinatura.");
        var account = lockBillingAccount(requestedAccountId);
        if (account.isSuspended()) throw error(403, "Conta suspensa.");
        if (account.getPaidUntil() != null && account.getPaidUntil().isAfter(Instant.now())
                && account.getPaidUntil().equals(account.getStripePixPaidUntil()))
            return subscriptionView(true, "pix_paid", account.getPaidUntil());

        String subscriptionId = account.getStripeSubscriptionId();
        try {
            if (account.getStripePixSessionId() != null) {
                var session = retrievePix(account.getStripePixSessionId());
                verifySession(account, session, account.getStripePixSessionId(), "payment");
                if ("complete".equals(session.getStatus()) && confirmPix(account, session.getId(), session.getClientReferenceId(), session.getMode(),
                        session.getPaymentStatus(), session.getCurrency(), session.getAmountTotal(), session.getCustomer())) {
                    return subscriptionView(true, "pix_paid", account.getPaidUntil());
                }
                if (pixClosedWithoutPayment(session)) {
                    clearPix(account);
                    return subscriptionView(false, "expired", null);
                }
                return subscriptionView(false, session.getPaymentStatus(), null);
            }
            if (account.getStripeCheckoutSessionId() != null) {
                Session session = stripe.v1().checkout().sessions().retrieve(
                        account.getStripeCheckoutSessionId(), SessionRetrieveParams.builder().build());
                verifySession(account, session, account.getStripeCheckoutSessionId(), FIELD_SUBSCRIPTION);
                if (!"complete".equals(session.getStatus()) || !"paid".equals(session.getPaymentStatus()))
                    return subscriptionView(false, "expired".equals(session.getStatus()) ? "expired" : session.getPaymentStatus(), null);
                if (session.getAmountTotal() == null || session.getAmountTotal() != monthlyPriceCents
                        || !"brl".equalsIgnoreCase(session.getCurrency()))
                    throw error(409, "O pagamento confirmado não corresponde ao plano mensal configurado.");
                subscriptionId = session.getSubscription();
                if (subscriptionId == null || subscriptionId.isBlank())
                    return subscriptionView(false, "subscription_pending", null);
            }
            if (subscriptionId == null || subscriptionId.isBlank())
                return subscriptionView(false, "checkout_not_found", null);

            Subscription subscription = stripe.v1().subscriptions().retrieve(subscriptionId,
                    SubscriptionRetrieveParams.builder().addExpand("latest_invoice").build());
            if (!subscriptionId.equals(subscription.getId()) || (account.getStripeSubscriptionId() != null
                    && !account.getStripeSubscriptionId().equals(subscription.getId())))
                throw error(409, "A assinatura Stripe não corresponde a esta conta.");
            if (!sameCustomer(account, subscription.getCustomer()))
                throw error(409, "O cliente Stripe da assinatura não corresponde a esta conta.");
            String metadataAccountId = subscription.getMetadata() == null ? null
                    : subscription.getMetadata().get(METADATA_PLATFORM_ACCOUNT_ID);
            if (!account.getId().toString().equals(metadataAccountId))
                throw error(409, "A assinatura Stripe não está vinculada a esta conta.");

            if (terminal(subscription.getStatus())) {
                if (matchesSubscription(account, subscription.getId()) && !terminal(account.getStripeSubscriptionStatus()))
                    endSubscription(account, subscription.getStatus(), Instant.now().getEpochSecond());
                return subscriptionView(false, subscription.getStatus(), null);
            }
            if (matchesSubscription(account, subscription.getId()) && terminal(account.getStripeSubscriptionStatus()))
                return subscriptionView(false, account.getStripeSubscriptionStatus(), null);

            var latestInvoice = subscription.getLatestInvoiceObject();
            JsonNode invoice = null;
            // Stripe objects use snake_case in their canonical JSON serialization.
            try {
                if (latestInvoice != null) invoice = json.readTree(latestInvoice.toJson());
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw error(502, "Não foi possível validar a fatura Stripe.");
            }
            Instant periodEnd = invoice == null ? null : invoicePeriodEnd(invoice);
            if (!STATUS_ACTIVE.equals(subscription.getStatus()) || invoice == null || !paidInvoice(invoice)
                    || !subscriptionId.equals(subscriptionId(invoice)) || !sameCustomer(account, text(invoice.path(FIELD_CUSTOMER)))
                    || periodEnd == null || !periodEnd.isAfter(Instant.now()))
                return subscriptionView(false, subscription.getStatus(), null);

            account.setStripeCustomerId(subscription.getCustomer());
            account.setStripeSubscriptionId(subscription.getId());
            account.setStripeSubscriptionStatus(subscription.getStatus());
            account.setProfessional(true);
            if (account.getPaidUntil() == null || periodEnd.isAfter(account.getPaidUntil())) account.setPaidUntil(periodEnd);
            clearCard(account);
            accounts.save(account);
            return subscriptionView(true, subscription.getStatus(), account.getPaidUntil());
        } catch (StripeException ex) {
            throw error(502, "Não foi possível consultar a assinatura no Stripe.");
        }
    }

    private Map<String, Object> subscriptionView(boolean active, String status, Instant paidUntil) {
        var response = new LinkedHashMap<String, Object>();
        response.put("active", active);
        response.put(FIELD_STATUS, status == null ? "pending" : status);
        response.put("paidUntil", paidUntil == null ? null : paidUntil.toString());
        return response;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void verifyAndProcess(String payload, String signature) {
        if (!enabled) throw error(503, "Webhook Stripe indisponível.");
        final Event event;
        final JsonNode object;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
            object = json.readTree(payload).path("data").path("object");
        } catch (Exception ex) {
            throw error(400, "Envelope ou assinatura do webhook inválida.");
        }
        if (event.getId() == null || !event.getId().matches("evt_[A-Za-z0-9_]{1,240}")
                || event.getType() == null || event.getType().length() > 100
                || event.getCreated() == null || event.getCreated() <= 0
                || !Boolean.valueOf(liveMode).equals(event.getLivemode()) || !object.isObject()
                || text(object.path("id")) == null)
            throw error(400, "Envelope do evento Stripe inválido.");
        // O INSERT disputa a chave primária antes de tocar na conta. No MySQL e H2 o
        // erro de chave duplicada do JDBC reverte só a instrução, sem invalidar a transação.
        try {
            jdbc.update("insert into stripe_webhook_event (id, type, processed_at) values (?, ?, ?)",
                    event.getId(), event.getType(), Timestamp.from(Instant.now()));
        } catch (DuplicateKeyException ex) {
            return;
        } catch (org.springframework.dao.DataAccessException ex) {
            throw error(503, "Não foi possível registrar o evento Stripe.");
        }
        try {
            long created = event.getCreated();
            switch (event.getType()) {
                case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> {
                    if ("payment".equals(text(object.path("mode")))) pixEvent(object);
                    else if ("checkout.session.completed".equals(event.getType())) checkoutCompleted(object);
                }
                case "checkout.session.expired" -> checkoutExpired(object);
                case "checkout.session.async_payment_failed" -> pixFailed(object);
                case "invoice.paid" -> invoicePaid(object, created);
                case "invoice.payment_failed" -> invoiceFailed(object, created);
                case "customer.subscription.updated" -> subscriptionUpdated(object, created);
                case "customer.subscription.deleted" -> subscriptionDeleted(object, created);
                default -> { }
            }
            accounts.flush();
        } catch (StripeException | org.springframework.dao.DataAccessException ex) {
            throw error(503, "Não foi possível concluir o processamento do evento Stripe.");
        }
    }

    private PlatformAccount sessionAccount(JsonNode session) {
        String accountId = text(session.path("client_reference_id"));
        if (accountId == null || !accountId.matches("[0-9]{1,18}")) return null;
        var account = accounts.lockById(Long.valueOf(accountId)).orElse(null);
        return account != null && sameCustomer(account, text(session.path(FIELD_CUSTOMER))) ? account : null;
    }

    private void pixEvent(JsonNode session) {
        var account = sessionAccount(session);
        String id = text(session.path("id"));
        if (account == null || !id.equals(account.getStripePixSessionId())
                || !"complete".equals(text(session.path(FIELD_STATUS)))) return;
        confirmPix(account, id, text(session.path("client_reference_id")), text(session.path("mode")),
                text(session.path("payment_status")), text(session.path("currency")),
                session.path("amount_total").asLong(-1), text(session.path(FIELD_CUSTOMER)));
    }

    private boolean confirmPix(PlatformAccount account, String sessionId, String accountId, String mode,
            String paymentStatus, String currency, Long amount, String customer) {
        if (!"paid".equals(paymentStatus) || account.isSuspended()) return false;
        if (!java.util.Objects.equals(account.getStripePixSessionId(), sessionId) || !sameCustomer(account, customer)
                || !account.getId().toString().equals(accountId) || !"payment".equals(mode)
                || !"brl".equalsIgnoreCase(currency) || amount == null || account.getStripePixAmountCents() == null
                || amount.longValue() != account.getStripePixAmountCents().longValue())
            throw error(409, "O Pix confirmado não corresponde ao pedido da conta.");
        account.setProfessional(true);
        Instant now = Instant.now();
        Instant start = account.getPaidUntil() != null && account.getPaidUntil().isAfter(now)
                ? account.getPaidUntil() : now;
        Instant paidUntil = start.plus(30, ChronoUnit.DAYS);
        account.setPaidUntil(paidUntil);
        account.setStripePixPaidUntil(paidUntil);
        clearPix(account);
        return true;
    }

    private void clearPix(PlatformAccount account) {
        account.setStripePixSessionId(null);
        account.setStripePixAmountCents(null);
    }

    private void clearCard(PlatformAccount account) {
        account.setStripeCheckoutSessionId(null);
        account.setStripeCheckoutExpiresAt(null);
    }

    private void checkoutExpired(JsonNode session) {
        var account = sessionAccount(session);
        if (account == null || !"expired".equals(text(session.path(FIELD_STATUS)))
                || "paid".equals(text(session.path("payment_status")))) return;
        String id = text(session.path("id"));
        if ("payment".equals(text(session.path("mode"))) && id.equals(account.getStripePixSessionId()))
            clearPix(account);
        if (FIELD_SUBSCRIPTION.equals(text(session.path("mode"))) && id.equals(account.getStripeCheckoutSessionId()))
            clearCard(account);
    }

    private void pixFailed(JsonNode eventSession) throws StripeException {
        if (!"payment".equals(text(eventSession.path("mode")))) return;
        var account = sessionAccount(eventSession);
        String id = text(eventSession.path("id"));
        if (account == null || !id.equals(account.getStripePixSessionId())) return;
        var session = retrievePix(id);
        verifySession(account, session, id, "payment");
        if (pixClosedWithoutPayment(session)) clearPix(account);
    }

    private Session retrievePix(String id) throws StripeException {
        return stripe.v1().checkout().sessions().retrieve(id,
                SessionRetrieveParams.builder().addExpand("payment_intent").build());
    }

    private boolean pixClosedWithoutPayment(Session session) {
        var intent = session.getPaymentIntentObject();
        return !"paid".equals(session.getPaymentStatus()) && ("expired".equals(session.getStatus())
                || (intent != null && "canceled".equals(intent.getStatus())));
    }

    private void checkoutCompleted(JsonNode session) {
        var account = sessionAccount(session);
        String subscriptionId = text(session.path(FIELD_SUBSCRIPTION));
        if (account == null || account.isSuspended() || subscriptionId == null
                || !FIELD_SUBSCRIPTION.equals(text(session.path("mode")))
                || !"complete".equals(text(session.path(FIELD_STATUS)))
                || !text(session.path("id")).equals(account.getStripeCheckoutSessionId())
                || account.getStripeSubscriptionId() != null) return;
        account.setStripeSubscriptionId(subscriptionId);
        account.setStripeSubscriptionStatus("incomplete");
        // A sessão continua vinculada para a consulta ativa até a confirmação da fatura.
    }

    private void invoicePaid(JsonNode invoice, long created) throws StripeException {
        if (!paidInvoice(invoice)) return;
        String subscriptionId = subscriptionId(invoice);
        if (subscriptionId == null) return;
        var account = findCustomer(text(invoice.path(FIELD_CUSTOMER)));
        if (account == null || account.isSuspended() || terminal(account.getStripeSubscriptionStatus())
                || "paused".equals(account.getStripeSubscriptionStatus())) return;
        if (!matchesSubscription(account, subscriptionId)) {
            if (account.getStripeSubscriptionId() != null || account.getStripeCheckoutSessionId() == null) return;
            var session = stripe.v1().checkout().sessions().retrieve(account.getStripeCheckoutSessionId());
            verifySession(account, session, account.getStripeCheckoutSessionId(), FIELD_SUBSCRIPTION);
            if (!subscriptionId.equals(session.getSubscription())) return;
            if (!"complete".equals(session.getStatus()) || !"paid".equals(session.getPaymentStatus()))
                throw error(503, "A confirmação da sessão Stripe ainda não está disponível.");
        }
        Instant periodEnd = invoicePeriodEnd(invoice);
        if (periodEnd == null || !periodEnd.isAfter(Instant.now())) return;
        account.setStripeSubscriptionId(subscriptionId);
        clearCard(account);
        account.setProfessional(true);
        if (account.getPaidUntil() == null || periodEnd.isAfter(account.getPaidUntil())) account.setPaidUntil(periodEnd);
        if (!older(account, created)) {
            account.setStripeSubscriptionStatus(STATUS_ACTIVE);
            account.setStripeSubscriptionEventAt(created);
        }
    }

    private void invoiceFailed(JsonNode invoice, long created) {
        var account = findCustomer(text(invoice.path(FIELD_CUSTOMER)));
        if (!matchesSubscription(account, subscriptionId(invoice)) || older(account, created)
                || terminal(account.getStripeSubscriptionStatus()) || "paused".equals(account.getStripeSubscriptionStatus())
                || (STATUS_ACTIVE.equals(account.getStripeSubscriptionStatus())
                    && Long.valueOf(created).equals(account.getStripeSubscriptionEventAt()))) return;
        account.setStripeSubscriptionStatus("past_due");
        account.setStripeSubscriptionEventAt(created);
    }

    private void subscriptionUpdated(JsonNode subscription, long created) {
        var account = findCustomer(text(subscription.path(FIELD_CUSTOMER)));
        String status = text(subscription.path(FIELD_STATUS));
        if (!matchesSubscription(account, text(subscription.path("id"))) || status == null
                || terminal(account.getStripeSubscriptionStatus())) return;
        if (terminal(status)) {
            endSubscription(account, status, created);
        } else if (!older(account, created)) {
            if (Long.valueOf(created).equals(account.getStripeSubscriptionEventAt())
                    && ("paused".equals(account.getStripeSubscriptionStatus())
                        || (STATUS_ACTIVE.equals(account.getStripeSubscriptionStatus()) && !"paused".equals(status)))) return;
            account.setStripeSubscriptionStatus(status);
            account.setStripeSubscriptionEventAt(created);
        }
    }

    private void subscriptionDeleted(JsonNode subscription, long created) {
        var account = findCustomer(text(subscription.path(FIELD_CUSTOMER)));
        if (matchesSubscription(account, text(subscription.path("id"))) && !terminal(account.getStripeSubscriptionStatus()))
            endSubscription(account, "canceled", created);
    }

    private void endSubscription(PlatformAccount account, String status, long created) {
        account.setStripeSubscriptionStatus(status);
        account.setStripeSubscriptionEventAt(Math.max(created,
                account.getStripeSubscriptionEventAt() == null ? 0 : account.getStripeSubscriptionEventAt()));
        account.setProfessional(false);
        account.setPaidUntil(Instant.now());
        clearCard(account);
    }

    private PlatformAccount findCustomer(String customerId) {
        if (customerId == null) return null;
        return accounts.lockByStripeCustomerId(customerId).orElse(null);
    }

    private boolean sameCustomer(PlatformAccount account, String customerId) {
        return customerId != null && customerId.equals(account.getStripeCustomerId());
    }

    private boolean matchesSubscription(PlatformAccount account, String subscriptionId) {
        return account != null && subscriptionId != null && subscriptionId.equals(account.getStripeSubscriptionId());
    }

    private boolean older(PlatformAccount account, long created) {
        return account.getStripeSubscriptionEventAt() != null && created < account.getStripeSubscriptionEventAt();
    }

    private boolean terminal(String status) {
        return "canceled".equals(status) || "incomplete_expired".equals(status);
    }

    private String subscriptionId(JsonNode invoice) {
        String direct = text(invoice.path(FIELD_SUBSCRIPTION));
        if (direct != null) return direct;
        return text(invoice.path("parent").path("subscription_details").path(FIELD_SUBSCRIPTION));
    }

    private boolean paidInvoice(JsonNode invoice) {
        return "paid".equals(text(invoice.path(FIELD_STATUS))) && !invoice.path("paid_out_of_band").asBoolean(false)
                && "brl".equalsIgnoreCase(text(invoice.path("currency")))
                && invoice.path("amount_paid").asLong(-1) == monthlyPriceCents;
    }

    private Instant invoicePeriodEnd(JsonNode invoice) {
        long latest = 0;
        for (JsonNode line : invoice.path("lines").path("data")) {
            String linePrice = text(line.path("price").path("id"));
            if (linePrice == null) linePrice = text(line.path("pricing").path("price_details").path("price"));
            if (priceId.equals(linePrice) && "brl".equalsIgnoreCase(text(line.path("currency")))
                    && !line.path("proration").asBoolean(false)
                    && !line.path("parent").path("subscription_item_details").path("proration").asBoolean(false)
                    && line.path("amount").asLong(-1) == monthlyPriceCents)
                latest = Math.max(latest, line.path("period").path("end").asLong(0));
        }
        return latest > 0 ? Instant.ofEpochSecond(latest) : null;
    }

    private String text(JsonNode node) { return node.isMissingNode() || node.isNull() || node.isContainerNode() ? null : node.asText(); }
    private ResponseStatusException error(int status, String message) { return new ResponseStatusException(HttpStatus.valueOf(status), message); }
}
