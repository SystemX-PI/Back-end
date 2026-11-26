package com.systemX.senai.service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.systemX.senai.dto.EvolutionStatusResponse;

@Service
public class EvolutionService {
    private static final String API_KEY_HEADER = "apikey";
    private static final String INSTANCE_FIELD = "instance";
    private static final String STATE_FIELD = "state";
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String baseUrl;

    public EvolutionService(
            @Value("${app.evolution.url:http://localhost:8081}") String baseUrl,
            @Value("${app.evolution.api-key:}") String apiKey) {
        this.baseUrl = removeTrailingSlashes(baseUrl);
        this.restClient = RestClient.builder().baseUrl(this.baseUrl).requestFactory(requestFactory()).build();
        this.apiKey = apiKey;
    }

    public EvolutionStatusResponse generateQr(String instanceName) {
        requireConfiguration();
        String encodedName = UriUtils.encodePathSegment(instanceName, StandardCharsets.UTF_8);
        JsonNode current = getConnection(instanceName, encodedName);
        if (isOpen(current)) {
            return response(instanceName, current.path(INSTANCE_FIELD).path(STATE_FIELD).asText("open"), null, null, null);
        }

        JsonNode created = null;
        if (current == null) {
            try {
                created = post("/instance/create", Map.of(
                        "instanceName", instanceName,
                        "integration", "WHATSAPP-BAILEYS",
                        "qrcode", true));
            } catch (RestClientResponseException exception) {
                // Evolution returns an error when the name already exists. Continue only if it can be found.
                if (getConnection(instanceName, encodedName) == null) {
                    throw evolutionError(exception);
                }
            }
        }

        JsonNode qr = created;
        if (qr == null || firstText(qr, "qrcode.base64", "base64", "qrCode") == null) {
            qr = get("/instance/connect/" + encodedName);
        }

        String image = firstText(qr, "qrcode.base64", "base64", "qrCode");
        if (image != null && !image.startsWith("data:image/")) {
            image = "data:image/png;base64," + image;
        }
        String code = firstText(qr, "code", "qrcode.code");
        if (image == null && code == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "A Evolution respondeu, mas não retornou um QR Code. Tente gerar novamente.");
        }

        return response(instanceName, "connecting", code, image, firstText(qr, "pairingCode"));
    }

    public EvolutionStatusResponse getStatus(String instanceName) {
        requireConfiguration();
        String encodedName = UriUtils.encodePathSegment(instanceName, StandardCharsets.UTF_8);
        JsonNode current = getConnection(instanceName, encodedName);
        if (current == null) {
            return response(instanceName, "not_created", null, null, null);
        }
        String state = current.path(INSTANCE_FIELD).path(STATE_FIELD).asText("unknown");
        return response(instanceName, state, null, null, null);
    }

    public String health() {
        if (apiKey.isBlank()) return "UNCONFIGURED";
        try {
            get("/instance/fetchInstances");
            return "AVAILABLE";
        } catch (RestClientResponseException | ResponseStatusException ex) {
            return "UNAVAILABLE";
        }
    }

    public void disconnect(String instanceName) { deleteRemote("/instance/logout/", instanceName); }
    public void remove(String instanceName) { deleteRemote("/instance/delete/", instanceName); }
    private void deleteRemote(String path, String instanceName) {
        requireConfiguration();
        try {
            restClient.delete().uri(path + UriUtils.encodePathSegment(instanceName, StandardCharsets.UTF_8))
                .header(API_KEY_HEADER, apiKey).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if(e.getStatusCode().value()!=404) throw evolutionError(e);
        } catch(Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível atualizar a conexão do WhatsApp.");
        }
    }

    private JsonNode getConnection(String ignoredInstanceName, String encodedName) {
        try {
            return get("/instance/connectionState/" + encodedName);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return null;
            }
            throw evolutionError(exception);
        }
    }

    private JsonNode get(String path) {
        String body;
        try {
            body = restClient.get().uri(path).header(API_KEY_HEADER, apiKey).retrieve().body(String.class);
        } catch (RestClientResponseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível conectar à Evolution API em " + baseUrl + ".");
        }
        return parseResponse(body);
    }

    private JsonNode post(String path, Object requestBody) {
        String body;
        try {
            body = restClient.post().uri(path).header(API_KEY_HEADER, apiKey).body(requestBody).retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível conectar à Evolution API em " + baseUrl + ".");
        }
        return parseResponse(body);
    }

    private JsonNode parseResponse(String body) {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "A Evolution API retornou uma resposta inválida.");
        }
    }

    private ResponseStatusException evolutionError(RestClientResponseException exception) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "A Evolution API recusou a solicitação (HTTP " + exception.getStatusCode().value() + "). "
                        + "Confira a chave e o nome da instância.");
    }

    private void requireConfiguration() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A chave de autenticação da Evolution API não está configurada no backend.");
        }
    }

    private boolean isOpen(JsonNode response) {
        return response != null && "open".equalsIgnoreCase(response.path(INSTANCE_FIELD).path(STATE_FIELD).asText());
    }

    private String firstText(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode value = node;
            for (String part : path.split("\\.")) {
                value = value.path(part);
            }
            if (value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private EvolutionStatusResponse response(String name, String state, String code, String qr, String pairingCode) {
        return new EvolutionStatusResponse(name, state, code, qr, pairingCode);
    }

    private String removeTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
    private static org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        return factory;
    }
}
