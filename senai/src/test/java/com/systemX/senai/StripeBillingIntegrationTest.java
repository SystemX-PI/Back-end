package com.systemX.senai;

import com.stripe.StripeClient;
import com.stripe.model.Price;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.PriceRetrieveParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.systemX.senai.config.JwtService;
import com.systemX.senai.entity.PlatformAccount;
import com.systemX.senai.entity.Usuario;
import com.systemX.senai.repository.PlatformAccountRepository;
import com.systemX.senai.repository.StripeWebhookEventRepository;
import com.systemX.senai.repository.UsuarioRepository;
import com.systemX.senai.service.PlatformService;
import com.systemX.senai.service.StripeBillingService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.billing.stripe.enabled=true", "app.billing.stripe.secret-key=fixture-local",
        "app.billing.stripe.webhook-secret=assinatura-de-teste-local", "app.billing.stripe.monthly-price-id=price_fixture",
        "app.billing.stripe.monthly-price-cents=9990"
})
@AutoConfigureMockMvc
class StripeBillingIntegrationTest {
    private static final String WEBHOOK = "/api/platform/webhooks/stripe";
    private static final String CHECKOUT = "/api/platform/subscription/stripe-checkout";
    @Autowired MockMvc mvc;
    @Autowired StripeBillingService billing;
    @Autowired PlatformService platform;
    @Autowired UsuarioRepository users;
    @Autowired JwtService jwt;
    @Autowired StripeWebhookEventRepository events;
    @MockitoSpyBean PlatformAccountRepository accounts;
    private StripeClient stripe;
    private Object originalClient;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String port = System.getProperty("systemx.test.mysql.port");
        if (port == null) {
            registry.add("spring.datasource.url", () -> "jdbc:h2:mem:stripe-billing;MODE=MySQL;DB_CLOSE_DELAY=-1");
            return;
        }
        int mysqlPort = Integer.parseInt(port);
        if (mysqlPort < 1024 || mysqlPort > 65535) throw new IllegalArgumentException("Porta de teste inválida");
        registry.add("spring.datasource.url",
                () -> "jdbc:mysql://127.0.0.1:" + mysqlPort + "/systemx_lote03_test?allowPublicKeyRetrieval=true&sslMode=DISABLED");
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "systemx_lote03_test");
        registry.add("spring.datasource.password", () -> System.getenv("SYSTEMX_LOTE03_MYSQL_PASSWORD"));
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @BeforeEach void mockStripe() {
        originalClient = ReflectionTestUtils.getField(billing, "stripe");
        stripe = mock(StripeClient.class, RETURNS_DEEP_STUBS);
        ReflectionTestUtils.setField(billing, "stripe", stripe);
    }

    @AfterEach void restoreStripe() {
        ReflectionTestUtils.setField(billing, "stripe", originalClient);
    }

    @Test void webhookRejeitaAssinaturaInvalidaCorpoAlteradoEAmbienteIncorreto() throws Exception {
        var account = account();
        String id = eventId();
        String body = event(id, "checkout.session.completed", now(), pix(account, "unpaid", 9990));
        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body + " ")
                .header("Stripe-Signature", signature(body))).andExpect(status().isBadRequest());
        send(body.replace("\"livemode\":false", "\"livemode\":true")).andExpect(status().isBadRequest());
        assertFalse(events.existsById(id));
    }

    @Test void pixPendenteErradoOuDeOutraContaNaoAtivaEAceiteSoEstendeUmaVez() throws Exception {
        var a = pixAccount();
        var other = account();
        send(event(eventId(), "checkout.session.completed", now(), pix(a, "unpaid", 9990))).andExpect(status().isNoContent());
        send(event(eventId(), "checkout.session.async_payment_succeeded", now(),
                pix(a, "paid", 9990).replace(a.getStripeCustomerId(), other.getStripeCustomerId())))
                .andExpect(status().isNoContent());
        send(event(eventId(), "checkout.session.async_payment_succeeded", now(),
                pix(a, "paid", 9990).replace(a.getId().toString(), "999999999")))
                .andExpect(status().isNoContent());
        send(event(eventId(), "checkout.session.async_payment_succeeded", now(), pix(a, "paid", 45)))
                .andExpect(status().isConflict());
        assertFalse(read(a).isProfessional());
        assertFalse(read(other).isProfessional());
        String success = event(eventId(), "checkout.session.async_payment_succeeded", now(), pix(a, "paid", 9990));
        send(success).andExpect(status().isNoContent());
        Instant first = read(a).getPaidUntil();
        assertTrue(read(a).isProfessional());
        assertEquals(first, read(a).getStripePixPaidUntil());
        assertNull(read(a).getStripePixSessionId());
        send(success).andExpect(status().isNoContent());
        send(event(eventId(), "checkout.session.completed", now(), pix(a, "paid", 9990))).andExpect(status().isNoContent());
        assertEquals(first, read(a).getPaidUntil());
    }

    @Test void pixExpiradoLiberaSessaoSemBeneficioEIgnoraEventoDaSessaoAntiga() throws Exception {
        var a = pixAccount();
        send(event(eventId(), "checkout.session.expired", now(),
                pix(a, "unpaid", 9990).replace("\"complete\"", "\"expired\""))).andExpect(status().isNoContent());
        assertNull(read(a).getStripePixSessionId());
        send(event(eventId(), "checkout.session.async_payment_succeeded", now(), pix(a, "paid", 9990)))
                .andExpect(status().isNoContent());
        assertFalse(read(a).isProfessional());
    }

    @Test void repeticaoSimultaneaEReentregaAposRollbackSaoAtomicas() throws Exception {
        var a = pixAccount();
        String id = eventId();
        String body = event(id, "checkout.session.async_payment_succeeded", now(), pix(a, "paid", 9990));
        doThrow(new DataIntegrityViolationException("falha reversível de teste")).when(accounts).flush();
        send(body).andExpect(status().isServiceUnavailable());
        reset(accounts);
        assertFalse(events.existsById(id));
        assertFalse(read(a).isProfessional());
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> request = () -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return send(body).andReturn().getResponse().getStatus();
            };
            var first = pool.submit(request);
            var second = pool.submit(request);
            start.countDown();
            assertEquals(204, first.get(15, TimeUnit.SECONDS));
            assertEquals(204, second.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertTrue(events.existsById(id));
        assertTrue(read(a).isProfessional());
        assertTrue(read(a).getPaidUntil().isBefore(Instant.now().plusSeconds(31L * 86400)));
    }

    @Test void cartaoAguardaFaturaERenovacaoForaDeOrdemNaoDiminuiPeriodoNemReabreCancelada() throws Exception {
        var a = cardAccount();
        long time = now();
        long end = time + 30L * 86400;
        send(event(eventId(), "checkout.session.completed", time - 1, card(a)))
                .andExpect(status().isNoContent());
        assertFalse(read(a).isProfessional());
        send(event(eventId(), "invoice.paid", time, invoice(a, end).replace("\"brl\"", "\"usd\"")))
                .andExpect(status().isNoContent());
        assertFalse(read(a).isProfessional());
        send(event(eventId(), "invoice.paid", time, invoice(a, end))).andExpect(status().isNoContent());
        assertEquals(Instant.ofEpochSecond(end), read(a).getPaidUntil());
        long renewed = end + 30L * 86400;
        send(event(eventId(), "invoice.paid", time + 5, invoice(a, renewed))).andExpect(status().isNoContent());
        send(event(eventId(), "invoice.payment_failed", time + 1, invoice(a, end))).andExpect(status().isNoContent());
        send(event(eventId(), "invoice.paid", time + 1, invoice(a, end))).andExpect(status().isNoContent());
        assertEquals("active", read(a).getStripeSubscriptionStatus());
        assertEquals(Instant.ofEpochSecond(renewed), read(a).getPaidUntil());
        send(event(eventId(), "invoice.payment_failed", time + 6, invoice(a, renewed))).andExpect(status().isNoContent());
        assertEquals("past_due", read(a).getStripeSubscriptionStatus());
        send(event(eventId(), "customer.subscription.deleted", time + 7, subscription(a, "canceled")))
                .andExpect(status().isNoContent());
        send(event(eventId(), "customer.subscription.updated", time + 5, subscription(a, "active")))
                .andExpect(status().isNoContent());
        send(event(eventId(), "invoice.paid", time + 5, invoice(a, renewed))).andExpect(status().isNoContent());
        assertEquals("canceled", read(a).getStripeSubscriptionStatus());
        assertFalse(read(a).isProfessional());
    }

    @Test void contaSuspensaNaoAtivaAssinaturaOuPix() throws Exception {
        var a = pixAccount();
        a.setStripeSubscriptionId(subscriptionId(a));
        a.setSuspended(true);
        accounts.saveAndFlush(a);
        send(event(eventId(), "invoice.paid", now(), invoice(a, now() + 86400))).andExpect(status().isNoContent());
        send(event(eventId(), "checkout.session.async_payment_succeeded", now(), pix(a, "paid", 9990)))
                .andExpect(status().isNoContent());
        assertFalse(read(a).isProfessional());
    }

    @Test void checkoutCartaoConcorrenteReusaSessaoECheckoutAlheioRetorna404() throws Exception {
        var a = account();
        var owner = owner(a);
        var outsider = owner(account());
        stubPrice();
        var session = session(a, "cs_card_" + a.getId(), "subscription");
        when(stripe.v1().checkout().sessions().retrieve(session.getId())).thenReturn(session);
        var sessions = stripe.v1().checkout().sessions();
        when(sessions.create(any(SessionCreateParams.class), any(RequestOptions.class))).thenAnswer(call -> {
            var params = call.<SessionCreateParams>getArgument(0);
            assertEquals(SessionCreateParams.Mode.SUBSCRIPTION, params.getMode());
            assertEquals(java.util.List.of(SessionCreateParams.AllowedPaymentMethodType.CARD), params.getAllowedPaymentMethodTypes());
            return session;
        });
        checkout(outsider, a, "CARD").andExpect(status().isNotFound());
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> request = () -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return checkout(owner, a, "CARD").andReturn().getResponse().getStatus();
            };
            var one = pool.submit(request);
            var two = pool.submit(request);
            start.countDown();
            assertEquals(201, one.get(15, TimeUnit.SECONDS));
            assertEquals(201, two.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        verify(sessions, times(1)).create(any(SessionCreateParams.class), any(RequestOptions.class));
        assertEquals(session.getId(), read(a).getStripeCheckoutSessionId());
        assertFalse(read(a).isProfessional());
    }

    @Test void checkoutPixReutilizaChaveAposRespostaIncompleta() throws Exception {
        var a = account();
        var owner = owner(a);
        stubPrice();
        var issued = new java.util.ArrayList<String>();
        var session = session(a, "cs_pix_" + a.getId(), "payment");
        var incomplete = new Session();
        incomplete.setId(session.getId());
        when(stripe.v1().checkout().sessions().create(any(SessionCreateParams.class), any(RequestOptions.class)))
                .thenAnswer(call -> {
                    var params = call.<SessionCreateParams>getArgument(0);
                    assertEquals(SessionCreateParams.Mode.PAYMENT, params.getMode());
                    assertEquals(java.util.List.of(SessionCreateParams.AllowedPaymentMethodType.PIX), params.getAllowedPaymentMethodTypes());
                    assertEquals(9990L, params.getLineItems().get(0).getPriceData().getUnitAmount());
                    issued.add(call.<RequestOptions>getArgument(1).getIdempotencyKey());
                    return issued.size() == 1 ? incomplete : session;
                });
        checkout(owner, a, "PIX").andExpect(status().isBadGateway());
        assertNotNull(read(a).getStripeCheckoutAttempt());
        checkout(owner, a, "CARD").andExpect(status().isConflict());
        checkout(owner, a, "PIX").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertEquals(2, issued.size());
        assertEquals(issued.get(0), issued.get(1));
        assertNull(read(a).getStripeCheckoutAttempt());
        assertEquals(session.getId(), read(a).getStripePixSessionId());
    }

    @Test void pixComFalhaAguardaCancelamentoConfirmadoNoProvedor() throws Exception {
        var a = pixAccount();
        var session = session(a, a.getStripePixSessionId(), "payment");
        session.setStatus("complete");
        session.setPaymentStatus("unpaid");
        var intent = new com.stripe.model.PaymentIntent();
        intent.setId("pi_fixture");
        intent.setStatus("processing");
        session.setPaymentIntentObject(intent);
        when(stripe.v1().checkout().sessions().retrieve(eq(session.getId()),
                any(com.stripe.param.checkout.SessionRetrieveParams.class))).thenReturn(session);
        String body = pix(a, "unpaid", 9990);
        send(event(eventId(), "checkout.session.async_payment_failed", now(), body)).andExpect(status().isNoContent());
        assertNotNull(read(a).getStripePixSessionId());
        intent.setStatus("canceled");
        send(event(eventId(), "checkout.session.async_payment_failed", now(), body)).andExpect(status().isNoContent());
        assertNull(read(a).getStripePixSessionId());
        assertFalse(read(a).isProfessional());
    }

    @Test void consultaDeCartaoExigeFaturaDaAssinaturaEConciliaAntesDoWebhook() throws Exception {
        var a = cardAccount();
        var u = owner(a);
        var session = session(a, a.getStripeCheckoutSessionId(), "subscription");
        session.setStatus("complete");
        session.setPaymentStatus("paid");
        session.setAmountTotal(9990L);
        session.setCurrency("brl");
        session.setSubscription(subscriptionId(a));
        when(stripe.v1().checkout().sessions().retrieve(eq(session.getId()),
                any(com.stripe.param.checkout.SessionRetrieveParams.class))).thenReturn(session);
        var subscription = new com.stripe.model.Subscription();
        subscription.setId(subscriptionId(a));
        subscription.setCustomer(a.getStripeCustomerId());
        subscription.setStatus("active");
        subscription.setMetadata(java.util.Map.of("platform_account_id", a.getId().toString()));
        when(stripe.v1().subscriptions().retrieve(eq(subscriptionId(a)),
                any(com.stripe.param.SubscriptionRetrieveParams.class))).thenReturn(subscription);
        long end = now() + 30L * 86400;
        var invoice = com.stripe.net.ApiResource.GSON.fromJson(invoice(a, end), com.stripe.model.Invoice.class);
        invoice.setCurrency("usd");
        subscription.setLatestInvoiceObject(invoice);
        refresh(u).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        assertFalse(read(a).isProfessional());
        invoice.setCurrency("brl");
        refresh(u).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        assertEquals(Instant.ofEpochSecond(end), read(a).getPaidUntil());
        assertTrue(read(a).isProfessional());
    }

    private ResultActions refresh(Usuario user) throws Exception {
        return mvc.perform(post("/api/platform/subscription/refresh")
                .header("Authorization", "Bearer " + jwt.generate(user)));
    }

    private void stubPrice() throws Exception {
        var recurring = new Price.Recurring();
        recurring.setInterval("month");
        recurring.setIntervalCount(1L);
        var price = new Price();
        price.setActive(true);
        price.setUnitAmount(9990L);
        price.setCurrency("brl");
        price.setRecurring(recurring);
        when(stripe.v1().prices().retrieve(eq("price_fixture"), any(PriceRetrieveParams.class))).thenReturn(price);
    }

    private Session session(PlatformAccount account, String id, String mode) {
        Session session = new Session();
        session.setId(id);
        session.setCustomer(account.getStripeCustomerId());
        session.setClientReferenceId(account.getId().toString());
        session.setMode(mode);
        session.setStatus("open");
        session.setUrl("https://checkout.stripe.com/example");
        session.setExpiresAt(now() + 3600);
        return session;
    }

    private PlatformAccount account() {
        PlatformAccount a = new PlatformAccount();
        a.setNome("Conta Stripe fictícia");
        a.setBrandName("SystemX teste");
        a.setStripeCustomerId("cus_fixture_" + UUID.randomUUID().toString().replace("-", ""));
        return accounts.saveAndFlush(a);
    }

    private PlatformAccount pixAccount() {
        var a = account();
        a.setStripePixSessionId("cs_pix_" + a.getId());
        a.setStripePixAmountCents(9990);
        return accounts.saveAndFlush(a);
    }

    private PlatformAccount cardAccount() {
        var a = account();
        a.setStripeCheckoutSessionId("cs_card_" + a.getId());
        a.setStripeCheckoutExpiresAt(Instant.now().plusSeconds(3600));
        return accounts.saveAndFlush(a);
    }

    private Usuario owner(PlatformAccount a) {
        var registered = platform.signup("Empresa fictícia", UUID.randomUUID() + "@example.test", "senha-ficticia");
        var owner = users.findById(registered.id()).orElseThrow();
        owner.setContaId(a.getId());
        return users.saveAndFlush(owner);
    }

    private PlatformAccount read(PlatformAccount a) {
        return accounts.findById(a.getId()).orElseThrow();
    }

    private ResultActions checkout(Usuario u, PlatformAccount a, String method) throws Exception {
        return mvc.perform(post(CHECKOUT).param("accountId", a.getId().toString())
                .header("Authorization", "Bearer " + jwt.generate(u))
                .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"" + method + "\"}"));
    }

    private ResultActions send(String body) throws Exception {
        return mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("Stripe-Signature", signature(body)));
    }

    private String signature(String body) throws Exception {
        long timestamp = now();
        Mac hmac = Mac.getInstance("HmacSHA256");
        var value = "assinatura-de-teste-local".getBytes(StandardCharsets.UTF_8);
        hmac.init(new SecretKeySpec(value, "HmacSHA256"));
        var hash = hmac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(hash);
    }

    private long now() { return Instant.now().getEpochSecond(); }
    private String eventId() { return "evt_" + UUID.randomUUID().toString().replace("-", ""); }
    private String subscriptionId(PlatformAccount a) { return "sub_fixture_" + a.getId(); }

    private String event(String id, String type, long time, String object) {
        return """
                {"id":"%s","object":"event","type":"%s","created":%d,"livemode":false,"data":{"object":%s}}
                """.formatted(id, type, time, object);
    }

    private String pix(PlatformAccount a, String paymentStatus, long amount) {
        return """
                {"id":"%s","mode":"payment","status":"complete","client_reference_id":"%d","customer":"%s",
                "payment_status":"%s","currency":"brl","amount_total":%d}
                """.formatted(a.getStripePixSessionId(), a.getId(), a.getStripeCustomerId(), paymentStatus, amount);
    }

    private String card(PlatformAccount a) {
        return """
                {"id":"%s","mode":"subscription","status":"complete","client_reference_id":"%d",
                "customer":"%s","subscription":"%s","payment_status":"unpaid"}
                """.formatted(a.getStripeCheckoutSessionId(), a.getId(), a.getStripeCustomerId(), subscriptionId(a));
    }

    private String invoice(PlatformAccount a, long periodEnd) {
        return """
                {"id":"in_fixture","customer":"%s","parent":{"type":"subscription_details","subscription_details":{"subscription":"%s"}},
                 "status":"paid","currency":"brl","amount_paid":9990,"lines":{"data":[{"pricing":{"type":"price_details",
                 "price_details":{"price":"price_fixture"}},"currency":"brl","amount":9990,"period":{"end":%d}}]}}
                """.formatted(a.getStripeCustomerId(), subscriptionId(a), periodEnd);
    }

    private String subscription(PlatformAccount a, String status) {
        return """
                {"id":"%s","customer":"%s","status":"%s"}
                """.formatted(subscriptionId(a), a.getStripeCustomerId(), status);
    }
}
