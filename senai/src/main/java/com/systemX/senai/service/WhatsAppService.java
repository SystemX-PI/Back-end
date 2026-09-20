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
    private final String apiKey;
    private final String instance;

    public WhatsAppService(
            @Value("${app.whatsapp.enabled:false}") boolean enabled,
            @Value("${app.whatsapp.api-url:http://localhost:8081}") String apiUrl,
            @Value("${app.whatsapp.api-key:}") String apiKey,
            @Value("${app.whatsapp.instance:consorcio}") String instance) {
        this.restClient = RestClient.builder().baseUrl(apiUrl).build();
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.instance = instance;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void send(String recipient, String text) {
        if (apiKey.isBlank() || instance.isBlank()) {
            throw new IllegalStateException(
                    "WhatsApp nao configurado. Informe EVOLUTION_API_KEY e EVOLUTION_INSTANCE.");
        }

        String phone = recipient.replaceAll("\\D", "");
        if (phone.isBlank()) {
            throw new IllegalArgumentException("Telefone do WhatsApp invalido: " + recipient);
        }

        try {
            restClient.post()
                    .uri("/message/sendText/{instance}", instance)
                    .header("apikey", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("number", phone, "text", text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IllegalStateException("Falha ao enviar mensagem pela Evolution API: "
                    + exception.getMessage(), exception);
        }
    }
}