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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
    private final GoogleIdentityService googleIdentityService;
    private final PlatformService platformService;

    public AuthService(
            UsuarioRepository usuarioRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.jwt.refresh-expiration-seconds:604800}") long refreshExpirationSeconds,
            GoogleIdentityService googleIdentityService,
            PlatformService platformService) {
        this.usuarioRepository = usuarioRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshExpirationSeconds = refreshExpirationSeconds;
        this.googleIdentityService = googleIdentityService;
        this.platformService = platformService;
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
    public AuthResponse loginWithGoogle(String credential) {
        GoogleIdentityService.Identity identity = googleIdentityService.verify(credential);
        Usuario usuario = usuarioRepository.findByGoogleSubject(identity.subject()).orElse(null);
        if (usuario != null) {
            if (!usuario.isAtivo()) throw new InvalidCredentialsException();
            return issueTokens(usuario);
        }

        if (usuarioRepository.lockByEmail(identity.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe uma conta com este e-mail. Entre com sua senha e vincule o Google no perfil.");
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Esta conta Google ainda não possui cadastro. Use Fazer Cadastro com o Google primeiro.");
    }

    @Transactional
    public AuthResponse registerWithGoogle(String credential, String companyName) {
        GoogleIdentityService.Identity identity = googleIdentityService.verify(credential);
        Usuario existing = usuarioRepository.findByGoogleSubject(identity.subject()).orElse(null);
        if (existing != null) {
            if (!existing.isAtivo()) throw new InvalidCredentialsException();
            return issueTokens(existing);
        }
        if (usuarioRepository.lockByEmail(identity.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este e-mail já possui cadastro. Entre pela tela de login ou vincule o Google pelo perfil.");
        }
        return issueTokens(platformService.signupWithGoogle(identity, companyName));
    }

    @Transactional
    public void linkGoogle(String credential, String authenticatedEmail) {
        GoogleIdentityService.Identity identity = googleIdentityService.verify(credential);
        Usuario usuario = usuarioRepository.lockByEmail(authenticatedEmail.trim().toLowerCase(java.util.Locale.ROOT))
                .filter(Usuario::isAtivo)
                .orElseThrow(InvalidCredentialsException::new);
        Usuario subjectOwner = usuarioRepository.findByGoogleSubject(identity.subject()).orElse(null);
        if (subjectOwner != null && !subjectOwner.getId().equals(usuario.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta conta Google já está vinculada a outro acesso.");
        }
        if (usuario.getGoogleSubject() != null && !usuario.getGoogleSubject().equals(identity.subject())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este usuário já possui outra conta Google vinculada.");
        }
        usuario.setGoogleSubject(identity.subject());
        usuarioRepository.save(usuario);
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
                jwtService.getExpirationSeconds(),
                usuario.getEmail());
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
