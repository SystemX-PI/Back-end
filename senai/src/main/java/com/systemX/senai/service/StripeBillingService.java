package com.systemX.senai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.systemX.senai.entity.PlatformAccount;
import com.systemX.senai.entity.StripeWebhookEvent;
import com.systemX.senai.enums.TipoUsuario;
import com.systemX.senai.repository.PlatformAccountRepository;
import com.systemX.senai.repository.StripeWebhookEventRepository;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Price;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PriceRetrieveParams;
import com.stripe.param.SubscriptionRetrieveParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import com.stripe.param.checkout.SessionCreateParams;
import java.time.Instant;
import java.util.LinkedHashMap;
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
    private final StripeWebhookEventRepository events;
    private final ObjectMapper json = new ObjectMapper();
    private final StripeClient stripe;
    private final boolean enabled;
    private final String webhookSecret;
    private final String priceId;
    private final int monthlyPriceCents;
    private final String successUrl;
    private final String cancelUrl;

    public StripeBillingService(PlatformService platform, PlatformAccountRepository accounts,
            StripeWebhookEventRepository events,
            @Value("${app.billing.stripe.enabled:false}") boolean enabled,
            @Value("${app.billing.stripe.secret-key:}") String secretKey,
            @Value("${app.billing.stripe.webhook-secret:}") String webhookSecret,
            @Value("${app.billing.stripe.monthly-price-id:}") String priceId,
            @Value("${app.billing.stripe.monthly-price-cents:0}") int monthlyPriceCents,
            @Value("${app.billing.stripe.success-url:http://localhost:5173/app/assinatura?stripe=success}") String successUrl,
            @Value("${app.billing.stripe.cancel-url:http://localhost:5173/app/upgrade?stripe=cancelled}") String cancelUrl) {
        this.platform = platform;
        this.accounts = accounts;
        this.events = events;
        this.enabled = enabled && !secretKey.isBlank() && !webhookSecret.isBlank()
                && !priceId.isBlank() && monthlyPriceCents > 0;
        this.stripe = this.enabled ? new StripeClient(secretKey) : null;
        this.webhookSecret = webhookSecret;
        this.priceId = priceId;
        this.monthlyPriceCents = monthlyPriceCents;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
    }

    @Transactional
    public Map<String, Object> createCheckout(Long requestedAccountId) {
        if (!enabled || stripe == null) throw error(503, "Checkout mensal ainda não configurado.");
        var user = platform.currentUser();
        if (user.getTipo() != TipoUsuario.CLIENTE && user.getTipo() != TipoUsuario.ADMINISTRADOR)
            throw error(403, "Somente o titular pode contratar um plano.");
        var account = platform.lockAccount(requestedAccountId);
        if (account.isSuspended()) throw error(403, "Conta suspensa.");
        if (account.isProfessional() && account.getPaidUntil() != null && account.getPaidUntil().isAfter(Instant.now()))
            throw error(409, "A assinatura Profissional já está ativa.");
        if (account.getStripeSubscriptionId() != null && (STATUS_ACTIVE.equals(account.getStripeSubscriptionStatus())
                || "trialing".equals(account.getStripeSubscriptionStatus())))
            throw error(409, "Já existe uma assinatura ativa para esta conta.");
        try {
            if (account.getStripeCheckoutSessionId() != null && account.getStripeCheckoutExpiresAt() != null
                    && account.getStripeCheckoutExpiresAt().isAfter(Instant.now())) {
                Session existing = stripe.v1().checkout().sessions().retrieve(account.getStripeCheckoutSessionId());
                if ("open".equals(existing.getStatus())) return checkoutView(existing.getUrl());
            }
            Price price = stripe.v1().prices().retrieve(priceId, PriceRetrieveParams.builder().build());
            if (!Boolean.TRUE.equals(price.getActive()) || price.getUnitAmount() == null || price.getUnitAmount() != monthlyPriceCents
                    || !"brl".equalsIgnoreCase(price.getCurrency()) || price.getRecurring() == null
                    || !INTERVAL_MONTH.equals(price.getRecurring().getInterval()))
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
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(customerId)
                    .setClientReferenceId(account.getId().toString())
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .setSubscriptionData(subscriptionData)
                    .addLineItem(SessionCreateParams.LineItem.builder().setPrice(priceId).setQuantity(1L).build())
                    .build();
            Session session = stripe.v1().checkout().sessions().create(params,
                    RequestOptions.builder().setIdempotencyKey("systemx-checkout-" + account.getId() + "-" + UUID.randomUUID()).build());
            account.setStripeCheckoutSessionId(session.getId());
            account.setStripeCheckoutExpiresAt(Instant.ofEpochSecond(session.getExpiresAt()));
            accounts.save(account);
            return checkoutView(session.getUrl());
        } catch (StripeException ex) {
            throw error(502, "Não foi possível iniciar o checkout no Stripe.");
        }
    }

    private Map<String, Object> checkoutView(String url) {
        var response = new LinkedHashMap<String, Object>();
        response.put("checkoutUrl", url);
        response.put(FIELD_STATUS, "PENDING");
        response.put("amountCents", monthlyPriceCents);
        response.put("currency", "BRL");
        response.put("interval", INTERVAL_MONTH);
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

    @Transactional
    public Map<String, Object> refreshSubscription(Long requestedAccountId) {
        if (!enabled || stripe == null) throw error(503, "Consulta de assinatura Stripe ainda não configurada.");
        var user = platform.currentUser();
        if (user.getTipo() != TipoUsuario.CLIENTE && user.getTipo() != TipoUsuario.ADMINISTRADOR)
            throw error(403, "Somente o titular pode consultar a assinatura.");
        var account = platform.lockAccount(requestedAccountId);
        if (account.isProfessional() && account.getPaidUntil() != null
                && account.getPaidUntil().isAfter(Instant.now())) {
            return subscriptionView(true, account.getStripeSubscriptionStatus(), account.getPaidUntil());
        }

        String subscriptionId = account.getStripeSubscriptionId();
        try {
            if (account.getStripeCheckoutSessionId() != null) {
                Session session = stripe.v1().checkout().sessions().retrieve(
                        account.getStripeCheckoutSessionId(), SessionRetrieveParams.builder().build());
                if (!account.getId().toString().equals(session.getClientReferenceId())
                        || !FIELD_SUBSCRIPTION.equals(session.getMode()))
                    throw error(409, "O checkout Stripe não corresponde a esta conta.");
                if (!"complete".equals(session.getStatus()) || !"paid".equals(session.getPaymentStatus()))
                    return subscriptionView(false, session.getPaymentStatus(), null);
                if (session.getAmountTotal() == null || session.getAmountTotal() != monthlyPriceCents
                        || !"brl".equalsIgnoreCase(session.getCurrency()))
                    throw error(409, "O pagamento confirmado não corresponde ao plano mensal configurado.");
                if (account.getStripeCustomerId() != null
                        && !account.getStripeCustomerId().equals(session.getCustomer()))
                    throw error(409, "O cliente Stripe do checkout não corresponde a esta conta.");
                subscriptionId = session.getSubscription();
                if (subscriptionId == null || subscriptionId.isBlank())
                    return subscriptionView(false, "subscription_pending", null);
            }
            if (subscriptionId == null || subscriptionId.isBlank())
                return subscriptionView(false, "checkout_not_found", null);

            Subscription subscription = stripe.v1().subscriptions().retrieve(subscriptionId,
                    SubscriptionRetrieveParams.builder().addExpand("latest_invoice").build());
            if (account.getStripeSubscriptionId() != null
                    && !account.getStripeSubscriptionId().equals(subscription.getId()))
                throw error(409, "A assinatura Stripe não corresponde a esta conta.");
            if (account.getStripeCustomerId() != null
                    && !account.getStripeCustomerId().equals(subscription.getCustomer()))
                throw error(409, "O cliente Stripe da assinatura não corresponde a esta conta.");
            String metadataAccountId = subscription.getMetadata() == null ? null
                    : subscription.getMetadata().get(METADATA_PLATFORM_ACCOUNT_ID);
            if (!account.getId().toString().equals(metadataAccountId))
                throw error(409, "A assinatura Stripe não está vinculada a esta conta.");

            boolean paidMonthlyItem = false;
            Instant periodEnd = null;
            if (subscription.getItems() != null && subscription.getItems().getData() != null) {
                for (SubscriptionItem item : subscription.getItems().getData()) {
                    Price itemPrice = item.getPrice();
                    if (itemPrice == null || !priceId.equals(itemPrice.getId()) || itemPrice.getUnitAmount() == null
                            || itemPrice.getUnitAmount() != monthlyPriceCents
                            || !"brl".equalsIgnoreCase(itemPrice.getCurrency()) || itemPrice.getRecurring() == null
                        || !INTERVAL_MONTH.equals(itemPrice.getRecurring().getInterval())) continue;
                    paidMonthlyItem = true;
                    if (item.getCurrentPeriodEnd() != null) {
                        Instant itemPeriodEnd = Instant.ofEpochSecond(item.getCurrentPeriodEnd());
                        if (periodEnd == null || itemPeriodEnd.isAfter(periodEnd)) periodEnd = itemPeriodEnd;
                    }
                }
            }
            var latestInvoice = subscription.getLatestInvoiceObject();
            if (!STATUS_ACTIVE.equals(subscription.getStatus()) || latestInvoice == null
                    || !"paid".equals(latestInvoice.getStatus()) || !paidMonthlyItem
                    || periodEnd == null || !periodEnd.isAfter(Instant.now()))
                return subscriptionView(false, subscription.getStatus(), null);

            account.setStripeCustomerId(subscription.getCustomer());
            account.setStripeSubscriptionId(subscription.getId());
            account.setStripeSubscriptionStatus(subscription.getStatus());
            account.setProfessional(true);
            account.setPaidUntil(periodEnd);
            account.setStripeCheckoutSessionId(null);
            account.setStripeCheckoutExpiresAt(null);
            accounts.save(account);
            return subscriptionView(true, subscription.getStatus(), periodEnd);
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

    @Transactional
    public void verifyAndProcess(String payload, String signature) {
        final Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (Exception ex) {
            throw error(400, "Assinatura do webhook inválida.");
        }
        process(event, payload);
    }

    public void process(Event event, String payload) {
        if (events.existsById(event.getId())) return;
        try {
            JsonNode object = json.readTree(payload).path("data").path("object");
            switch (event.getType()) {
                case "checkout.session.completed" -> checkoutCompleted(object);
                case "invoice.paid" -> invoicePaid(object);
                case "invoice.payment_failed" -> invoiceFailed(object);
                case "customer.subscription.updated" -> subscriptionUpdated(object);
                case "customer.subscription.deleted" -> subscriptionDeleted(object);
                default -> {
                    // Events outside the supported billing lifecycle are intentionally ignored.
                }
            }
            events.saveAndFlush(new StripeWebhookEvent(event.getId(), event.getType()));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw error(400, "Não foi possível processar o evento Stripe.");
        }
    }

    private void checkoutCompleted(JsonNode session) {
        String accountId = text(session.path("metadata").path(METADATA_PLATFORM_ACCOUNT_ID));
        if (accountId == null) accountId = text(session.path("client_reference_id"));
        String customerId = text(session.path(FIELD_CUSTOMER));
        String subscriptionId = text(session.path(FIELD_SUBSCRIPTION));
        var account = findAccount(accountId, customerId, subscriptionId);
        if (account == null || subscriptionId == null) return;
        if (!account.getId().toString().equals(accountId) && accountId != null) return;
        String sessionId = text(session.path("id"));
        if (account.getStripeCheckoutSessionId() != null && sessionId != null
                && !account.getStripeCheckoutSessionId().equals(sessionId)) return;
        if (customerId != null) account.setStripeCustomerId(customerId);
        account.setStripeSubscriptionId(subscriptionId);
        account.setStripeSubscriptionStatus("incomplete");
        account.setStripeCheckoutSessionId(null);
        account.setStripeCheckoutExpiresAt(null);
        accounts.save(account);
    }

    private void invoicePaid(JsonNode invoice) {
        if (!invoiceHasMonthlyPrice(invoice)) return;
        String subscriptionId = subscriptionId(invoice);
        var account = findAccount(null, text(invoice.path(FIELD_CUSTOMER)), subscriptionId);
        if (account == null) return;
        if (account.getStripeSubscriptionId() != null && subscriptionId != null
                && !account.getStripeSubscriptionId().equals(subscriptionId)) return;
        Instant periodEnd = invoicePeriodEnd(invoice);
        if (periodEnd == null || !periodEnd.isAfter(Instant.now())) return;
        if (subscriptionId != null) account.setStripeSubscriptionId(subscriptionId);
        account.setStripeSubscriptionStatus(STATUS_ACTIVE);
        account.setProfessional(true);
        account.setPaidUntil(periodEnd);
        accounts.save(account);
    }

    private void invoiceFailed(JsonNode invoice) {
        String subscriptionId = subscriptionId(invoice);
        var account = findAccount(null, text(invoice.path(FIELD_CUSTOMER)), subscriptionId);
        if (account != null && account.getStripeSubscriptionId() != null && subscriptionId != null
                && !account.getStripeSubscriptionId().equals(subscriptionId)) return;
        if (account != null) { account.setStripeSubscriptionStatus("past_due"); accounts.save(account); }
    }

    private void subscriptionUpdated(JsonNode subscription) {
        String subscriptionId = text(subscription.path("id"));
        var account = findAccount(null, text(subscription.path(FIELD_CUSTOMER)), subscriptionId);
        if (account == null) return;
        if (account.getStripeSubscriptionId() != null && subscriptionId != null
                && !account.getStripeSubscriptionId().equals(subscriptionId)) return;
        if (subscriptionId != null) account.setStripeSubscriptionId(subscriptionId);
        String status = text(subscription.path(FIELD_STATUS));
        account.setStripeSubscriptionStatus(status);
        accounts.save(account);
    }

    private void subscriptionDeleted(JsonNode subscription) {
        String subscriptionId = text(subscription.path("id"));
        var account = findAccount(null, text(subscription.path(FIELD_CUSTOMER)), subscriptionId);
        if (account == null) return;
        if (account.getStripeSubscriptionId() != null && subscriptionId != null
                && !account.getStripeSubscriptionId().equals(subscriptionId)) return;
        account.setStripeSubscriptionStatus("canceled");
        account.setProfessional(false);
        account.setPaidUntil(Instant.now());
        accounts.save(account);
    }

    private PlatformAccount findAccount(String accountId, String customerId, String subscriptionId) {
        if (accountId != null) {
            try { var found = accounts.findById(Long.parseLong(accountId)); if (found.isPresent()) return found.get(); }
            catch (NumberFormatException ignored) { }
        }
        if (customerId != null) { var found = accounts.findByStripeCustomerId(customerId); if (found.isPresent()) return found.get(); }
        if (subscriptionId != null) return accounts.findByStripeSubscriptionId(subscriptionId).orElse(null);
        return null;
    }

    private String subscriptionId(JsonNode invoice) {
        String direct = text(invoice.path(FIELD_SUBSCRIPTION));
        if (direct != null) return direct;
        return text(invoice.path("parent").path("subscription_details").path(FIELD_SUBSCRIPTION));
    }

    private Instant invoicePeriodEnd(JsonNode invoice) {
        long latest = invoice.path("period_end").asLong(0);
        for (JsonNode line : invoice.path("lines").path("data")) {
            latest = Math.max(latest, line.path("period").path("end").asLong(0));
        }
        return latest > 0 ? Instant.ofEpochSecond(latest) : null;
    }

    private boolean invoiceHasMonthlyPrice(JsonNode invoice) {
        for (JsonNode line : invoice.path("lines").path("data")) {
            String linePrice = text(line.path("price").path("id"));
            if (linePrice == null) linePrice = text(line.path("pricing").path("price_details").path("price"));
            if (priceId.equals(linePrice) && "brl".equalsIgnoreCase(text(line.path("currency")))) return true;
        }
        return false;
    }

    private String text(JsonNode node) { return node.isMissingNode() || node.isNull() || node.isContainerNode() ? null : node.asText(); }
    private ResponseStatusException error(int status, String message) { return new ResponseStatusException(HttpStatus.valueOf(status), message); }
}
