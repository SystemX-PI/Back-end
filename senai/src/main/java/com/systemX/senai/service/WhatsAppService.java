package com.systemX.senai.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class WhatsAppService {
    private final RestClient restClient;
    private final boolean enabled;
    private final String phoneNumberId;
    private final String accessToken;
    private final String apiVersion;

    public WhatsAppService(
            @Value("${app.whatsapp.enabled:false}") boolean enabled,
            @Value("${app.whatsapp.phone-number-id:}") String phoneNumberId,
            @Value("${app.whatsapp.access-token:}") String accessToken,
            @Value("${app.whatsapp.api-version:v23.0}") String apiVersion) {
        this.restClient = RestClient.builder().baseUrl("https://graph.facebook.com").build();
        this.enabled = enabled;
        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
        this.apiVersion = apiVersion;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void send(String recipient, String text) {
        if (phoneNumberId.isBlank() || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "WhatsApp nao configurado. Informe WHATSAPP_PHONE_NUMBER_ID e WHATSAPP_ACCESS_TOKEN.");
        }

        String phone = recipient.replaceAll("\\D", "");
        if (phone.isBlank()) {
            throw new IllegalArgumentException("Telefone do WhatsApp invalido: " + recipient);
        }

        try {
            restClient.post()
                    .uri("/{version}/{phoneNumberId}/messages", apiVersion, phoneNumberId)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "messaging_product", "whatsapp",
                            "to", phone,
                            "type", "text",
                            "text", Map.of("preview_url", false, "body", text)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IllegalStateException("Falha ao enviar mensagem pela WhatsApp Cloud API: "
                    + exception.getMessage(), exception);
        }
    }
}