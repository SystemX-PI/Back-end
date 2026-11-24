package com.systemX.senai.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

public final class IntegrationKeyFilter extends OncePerRequestFilter {
    public static final String AUTORIZADA = "systemx.integration.contemplacao.autorizada";
    static final PathPatternRequestMatcher ENTRADA = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST, "/api/integracoes/contemplacoes");

    private final byte[] configurada;

    IntegrationKeyFilter(String chave) {
        configurada = chave.isBlank() ? null : digest(chave);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ENTRADA.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String chave = request.getHeader("X-Integration-Key");
        if (configurada == null || chave == null || !MessageDigest.isEqual(configurada, digest(chave))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Integração não autorizada.");
            return;
        }
        request.setAttribute(AUTORIZADA, Boolean.TRUE);
        chain.doFilter(request, response);
    }

    private static byte[] digest(String chave) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(chave.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
