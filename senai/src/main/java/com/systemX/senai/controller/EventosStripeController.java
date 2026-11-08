package com.systemX.senai.controller;

import com.systemX.senai.service.StripeBillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/webhooks")
@Tag(name = "Eventos do Stripe", description = "Recepção de eventos assinados enviados diretamente pelo Stripe.")
public class EventosStripeController {
    private final StripeBillingService stripe;

    public EventosStripeController(StripeBillingService stripe) {
        this.stripe = stripe;
    }

    @Operation(summary = "Receber evento do Stripe", description = "O provedor envia o corpo original com o cabeçalho Stripe-Signature. Este endpoint valida a assinatura em vez de usar um JWT de usuário.")
    @SecurityRequirements
    @PostMapping("/stripe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stripeWebhook(@RequestBody String payload,
            @Parameter(description = "Assinatura do corpo original, gerada pelo Stripe.", required = true)
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        stripe.verifyAndProcess(payload, signature);
    }
}
