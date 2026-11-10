package com.systemX.senai.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

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
        this.restClient = RestClient.builder().baseUrl(apiUrl).requestFactory(requestFactory()).build();
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.instance = instance;
    }

    public String defaultInstance() { return instance; }

    public boolean isEnabled() {
        return enabled;
    }

    public void send(String recipient, String text) {
        sendFrom(instance, recipient, text);
    }

    public void sendFrom(String remoteInstance, String recipient, String text) {
        if (apiKey.isBlank() || remoteInstance.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "O serviço de WhatsApp ainda não está configurado.");
        }

        String phone = recipient.replaceAll("\\D", "");
        if (!recipient.strip().startsWith("+") && (phone.length() == 10 || phone.length() == 11)) {
            phone = "55" + phone;
        }
        if (!phone.matches("[1-9][0-9]{7,14}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um WhatsApp válido com DDI e DDD.");
        }

        try {
            restClient.post()
                    .uri("/message/sendText/{instance}", remoteInstance)
                    .header("apikey", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("number", phone, "text", text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            String message = switch (exception.getStatusCode().value()) {
                case 400, 422 -> "O WhatsApp recusou o envio. Confira se o destinatário está cadastrado no WhatsApp e inclua DDI e DDD.";
                case 401, 403 -> "O serviço de WhatsApp recusou a autenticação do remetente.";
                case 404 -> "O remetente não está disponível na Evolution. Confira sua conexão.";
                case 429 -> "O serviço de WhatsApp limitou os envios. Aguarde e tente novamente.";
                default -> "O serviço de WhatsApp não conseguiu processar o envio.";
            };
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível acessar o serviço de WhatsApp.");
        }
    }
    private static org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        return factory;
    }
}
