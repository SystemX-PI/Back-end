package com.systemX.senai.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systemX.senai.config.JwtService;
import com.systemX.senai.dto.AuthResponse;
import com.systemX.senai.dto.LoginRequest;
import com.systemX.senai.entity.RefreshToken;
import com.systemX.senai.entity.Usuario;
import com.systemX.senai.exception.InvalidCredentialsException;
import com.systemX.senai.repository.RefreshTokenRepository;
import com.systemX.senai.repository.UsuarioRepository;

@Service
public class AuthService {
    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshExpirationSeconds;

    public AuthService(
            UsuarioRepository usuarioRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.jwt.refresh-expiration-seconds:604800}") long refreshExpirationSeconds) {
        this.usuarioRepository = usuarioRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshExpirationSeconds = refreshExpirationSeconds;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .filter(Usuario::isAtivo)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.senha(), usuario.getSenha())) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(usuario);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .filter(token -> !token.isRevoked())
                .filter(token -> token.getExpiresAt().isAfter(Instant.now()))
                .filter(token -> token.getUsuario().isAtivo())
                .filter(token -> token.getVersaoAutenticacao() == token.getUsuario().getVersaoAutenticacao())
                .orElseThrow(InvalidCredentialsException::new);
        refreshToken.revoke();
        return issueTokens(refreshToken.getUsuario());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)).ifPresent(RefreshToken::revoke);
    }

    private AuthResponse issueTokens(Usuario usuario) {
        String rawRefreshToken = UUID.randomUUID() + UUID.randomUUID().toString();
        RefreshToken refreshToken = new RefreshToken(
                hash(rawRefreshToken),
                Instant.now().plusSeconds(refreshExpirationSeconds),
                usuario);
        refreshTokenRepository.save(refreshToken);
        return new AuthResponse(
                jwtService.generate(usuario),
                rawRefreshToken,
                usuario.getTipo().name(),
                jwtService.getExpirationSeconds());
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }
}