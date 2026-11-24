package com.systemX.senai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class IntegrationKeyFilterTest {
    @Test
    void servidorSemChaveRecusaEntrada() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/integracoes/contemplacoes");
        request.addHeader("X-Integration-Key", "valor-ficticio");
        var response = new MockHttpServletResponse();
        new IntegrationKeyFilter("").doFilter(request, response,
                (incoming, outgoing) -> { throw new AssertionError("Entrada sem chave configurada"); });
        assertEquals(403, response.getStatus());
        assertNull(request.getAttribute(IntegrationKeyFilter.AUTORIZADA));
    }
}
