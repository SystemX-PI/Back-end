package com.systemX.senai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.systemX.senai.service.GoogleIdentityService;

class GoogleIdentityServiceTest {
    @Test
    void refusesGoogleLoginUntilARealClientIdIsConfigured() {
        GoogleIdentityService service = new GoogleIdentityService("");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.verify("not-a-real-token"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }

    @Test
    void malformedGoogleTokensReturnAnExplicitUnauthorizedError() {
        GoogleIdentityService service = new GoogleIdentityService("local-client.apps.googleusercontent.com");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.verify("not-a-real-token"));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        assertEquals("O Google retornou uma autenticação inválida. Atualize a página e entre com Google novamente.",
                error.getReason());
    }
}
