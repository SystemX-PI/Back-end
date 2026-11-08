package com.systemX.senai.controller;

import com.systemX.senai.service.PlatformCheckoutService;
import com.systemX.senai.service.StripeBillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/subscription")
@Tag(name = "Assinaturas", description = "Contratação e gestão do plano por titulares ou administradores. Simulação e Stripe são fluxos separados.")
public class AssinaturaController {
    private final PlatformCheckoutService checkout;
    private final StripeBillingService stripe;

    public AssinaturaController(PlatformCheckoutService checkout, StripeBillingService stripe) {
        this.checkout = checkout;
        this.stripe = stripe;
    }

    public record Checkout(@NotBlank @Pattern(regexp = "PIX|CARD") String paymentMethod) { }

    @Operation(summary = "Iniciar uma compra simulada", description = "Aceita PIX ou CARD. Disponível apenas quando a simulação está habilitada; não gera cobrança.")
    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public Object checkout(@RequestParam(required = false) Long accountId, @Valid @RequestBody Checkout p) {
        return checkout.create(accountId, p.paymentMethod());
    }

    @Operation(summary = "Confirmar uma compra simulada", description = "Confirma o pedido de teste identificado por id. Não é uma confirmação de pagamento do Stripe.")
    @PostMapping("/checkout/{id}/confirm")
    public Object confirm(@RequestParam(required = false) Long accountId, @PathVariable String id) {
        return checkout.confirm(accountId, id);
    }

    @Operation(summary = "Abrir o checkout mensal do Stripe", description = "Gera a sessão para contratar o plano quando o Stripe está configurado.")
    @PostMapping("/stripe-checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public Object stripeCheckout(@RequestParam(required = false) Long accountId,
            @Valid @RequestBody(required = false) Checkout payment) {
        return stripe.createCheckout(accountId, payment != null && "PIX".equals(payment.paymentMethod()));
    }

    @Operation(summary = "Abrir o portal do assinante no Stripe")
    @PostMapping("/portal")
    public Object stripePortal(@RequestParam(required = false) Long accountId) {
        return stripe.createPortalSession(accountId);
    }

    @Operation(summary = "Sincronizar a assinatura com o Stripe", description = "Consulta o provedor e atualiza a situação do plano da conta.")
    @PostMapping("/refresh")
    public Object stripeRefresh(@RequestParam(required = false) Long accountId) {
        return stripe.refreshSubscription(accountId);
    }
}
