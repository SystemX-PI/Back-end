package com.systemX.senai.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.systemX.senai.entity.Usuario;
import com.systemX.senai.repository.UsuarioRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final boolean devTokenEnabled;
    private final String devToken;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UsuarioRepository usuarioRepository,
            @Value("${app.security.dev-token-enabled:false}") boolean devTokenEnabled,
            @Value("${app.security.dev-token:}") String devToken) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
        this.devTokenEnabled = devTokenEnabled;
        this.devToken = devToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (isDevToken(token)) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        "dev-user", null, java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                chain.doFilter(request, response);
                return;
            }
            try {
                Claims claims = jwtService.parse(token);
                Usuario usuario = usuarioRepository.findByEmail(claims.getSubject()).orElse(null);
                if (usuario != null && usuario.isAtivo()) {
                    var authorities = java.util.List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getTipo().name()));
                    var authentication = new UsernamePasswordAuthenticationToken(usuario.getEmail(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isDevToken(String token) {
        return devTokenEnabled
                && !devToken.isBlank()
                && MessageDigest.isEqual(
                        token.getBytes(StandardCharsets.UTF_8),
                        devToken.getBytes(StandardCharsets.UTF_8));
    }
}