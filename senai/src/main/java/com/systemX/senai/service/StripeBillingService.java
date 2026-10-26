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
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PriceRetrieveParams;
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
        if (account.getStripeSubscriptionId() != null && ("active".equals(account.getStripeSubscriptionStatus())
                || "trialing".equals(account.getStripeSubscriptionStatus())))
            throw error(409, "Já existe uma assinatura ativa para esta conta.");
        try {
            if (account.getStripeCheckoutSessionId() != null && account.getStripeCheckoutExpiresAt() != null
                    && account.getStripeCheckoutExpiresAt().isAfter(Instant.now())) {
                Session existing = stripe.v1().checkout().sessions().retrieve(account.getStripeCheckoutSessionId());
                if ("open".equals(existing.getStatus())) return checkoutView(existing.getUrl());
            }
            Price price = stripe.v1().prices().retrieve(priceId, PriceRetrieveParams.builder().build());
            if (!price.getActive() || price.getUnitAmount() == null || price.getUnitAmount() != monthlyPriceCents
                    || !"brl".equalsIgnoreCase(price.getCurrency()) || price.getRecurring() == null
                    || !"month".equals(price.getRecurring().getInterval()))
                throw error(503, "O preço Stripe configurado não corresponde a uma assinatura mensal em BRL.");

            String customerId = account.getStripeCustomerId();
            if (customerId == null || customerId.isBlank()) {
                var customerParams = CustomerCreateParams.builder()
                        .setEmail(account.getOwnerEmail())
                        .putMetadata("platform_account_id", account.getId().toString())
                        .build();
                Customer customer = stripe.v1().customers().create(customerParams,
                        RequestOptions.builder().setIdempotencyKey("systemx-customer-" + account.getId()).build());
                customerId = customer.getId();
                account.setStripeCustomerId(customerId);
                accounts.save(account);
            }
            var subscriptionData = SessionCreateParams.SubscriptionData.builder()
                    .putMetadata("platform_account_id", account.getId().toString()).build();
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
        response.put("status", "PENDING");
        response.put("amountCents", monthlyPriceCents);
        response.put("currency", "BRL");
        response.put("interval", "month");
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
                default -> { }
            }
            events.saveAndFlush(new StripeWebhookEvent(event.getId(), event.getType()));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw error(400, "Não foi possível processar o evento Stripe.");
        }
    }

    private void checkoutCompleted(JsonNode session) {
        String accountId = text(session.path("metadata").path("platform_account_id"));
        if (accountId == null) accountId = text(session.path("client_reference_id"));
        String customerId = text(session.path("customer"));
        String subscriptionId = text(session.path("subscription"));
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
        var account = findAccount(null, text(invoice.path("customer")), subscriptionId);
        if (account == null) return;
        if (account.getStripeSubscriptionId() != null && subscriptionId != null
                && !account.getStripeSubscriptionId().equals(subscriptionId)) return;
        Instant periodEnd = invoicePeriodEnd(invoice);
        if (periodEnd == null || !periodEnd.isAfter(Instant.now())) return;
        if (subscriptionId != null) account.setStripeSubscriptionId(subscriptionId);
        account.setStripeSubscriptionStatus("active");
        account.setProfessional(true);
        account.setPaidUntil(periodEnd);
        accounts.save(account);
    }

    private void invoiceFailed(JsonNode invoice) {
        String subscriptionId = subscriptionId(invoice);
        var account = findAccount(null, text(invoice.path("customer")), subscriptionId);
        if (account != null && account.getStripeSubscriptionId() != null && subscriptionId != null
                && !account.getStripeSubscriptionId().equals(subscriptionId)) return;
        if (account != null) { account.setStripeSubscriptionStatus("past_due"); accounts.save(account); }
    }

    private void subscriptionUpdated(JsonNode subscription) {
        String subscriptionId = text(subscription.path("id"));
        var account = findAccount(null, text(subscription.path("customer")), subscriptionId);
        if (account == null) return;
        if (account.getStripeSubscriptionId() != null && subscriptionId != null
                && !account.getStripeSubscriptionId().equals(subscriptionId)) return;
        if (subscriptionId != null) account.setStripeSubscriptionId(subscriptionId);
        String status = text(subscription.path("status"));
        account.setStripeSubscriptionStatus(status);
        accounts.save(account);
    }

    private void subscriptionDeleted(JsonNode subscription) {
        String subscriptionId = text(subscription.path("id"));
        var account = findAccount(null, text(subscription.path("customer")), subscriptionId);
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
        String direct = text(invoice.path("subscription"));
        if (direct != null) return direct;
        return text(invoice.path("parent").path("subscription_details").path("subscription"));
    }

    private Instant invoicePeriodEnd(JsonNode invoice) {
        long direct = invoice.path("period_end").asLong(0);
        if (direct > 0) return Instant.ofEpochSecond(direct);
        long latest = 0;
        for (JsonNode line : invoice.path("lines").path("data")) latest = Math.max(latest, line.path("period").path("end").asLong(0));
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
