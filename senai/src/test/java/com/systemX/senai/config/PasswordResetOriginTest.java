package com.systemX.senai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PasswordResetOriginTest {
    @Test
    void cabecalhoSoValeParaProxyExplicitamenteConfiavel() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For", "192.0.2.20");
        request.addHeader("X-Real-IP", "192.0.2.30");
        assertEquals("192.0.2.10", new PasswordResetOrigin("").resolve(request));
        assertEquals("192.0.2.30", new PasswordResetOrigin("192.0.2.10").resolve(request));
    }
}
