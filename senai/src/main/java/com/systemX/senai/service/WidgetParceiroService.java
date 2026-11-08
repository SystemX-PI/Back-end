package com.systemX.senai.service;

import com.systemX.senai.entity.PlatformAccount;
import com.systemX.senai.enums.CanalComunicacao;
import com.systemX.senai.enums.TipoUsuario;
import com.systemX.senai.repository.PlatformAccountRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WidgetParceiroService {
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();
    private static final int SESSION_SECONDS = 300;
    private final SecureRandom random = new SecureRandom();
    private final PlatformService platform;
    private final PlatformPlans plans;
    private final PlatformAccountRepository accounts;
    private final NotificationHistoryService history;
    private final SecretKeySpec signingKey;

    public WidgetParceiroService(PlatformService platform, PlatformPlans plans,
            PlatformAccountRepository accounts, NotificationHistoryService history,
            @Value("${app.jwt.secret}") String signingSecret) {
        this.platform = platform;
        this.plans = plans;
        this.accounts = accounts;
        this.history = history;
        this.signingKey = new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public record Configuracao(Long accountId, String origin, String key) {}
    public record Sessao(String token, int expiresIn) {}
    public record Resumo(String empresa, long total, long aceitas, long pendentes, long falhas) {}

    @Transactional(readOnly = true)
    public Configuracao configuracao(Long id) {
        verificarTitular();
        var account = platform.account(id);
        verificarPlano(account);
        return new Configuracao(account.getId(), account.getWidgetOrigin(), null);
    }

    @Transactional
    public Configuracao configurar(Long id, String origin) {
        verificarTitular();
        var account = platform.lockAccount(id);
        verificarPlano(account);
        String allowedOrigin = origem(origin);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String key = BASE64.encodeToString(bytes);
        account.setWidgetOrigin(allowedOrigin);
        account.setWidgetKeyDigest(digest(key));
        return new Configuracao(account.getId(), allowedOrigin, key);
    }

    @Transactional
    public void desativar(Long id) {
        verificarTitular();
        var account = platform.lockAccount(id);
        account.setWidgetOrigin(null);
        account.setWidgetKeyDigest(null);
    }

    @Transactional(readOnly = true)
    public Sessao sessao(Long id, String key) {
        var account = instalado(id);
        if (key.length() != 43 || !equal(digest(key), account.getWidgetKeyDigest())) throw invalido();
        verificarPlano(account);
        String claims = id + "." + Instant.now().plusSeconds(SESSION_SECONDS).getEpochSecond();
        return new Sessao(claims + "." + signature(claims, account), SESSION_SECONDS);
    }

    @Transactional(readOnly = true)
    public Resumo consultar(String token, String origin, String channel, int days) {
        if (token.length() > 90) throw invalido();
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) throw invalido();
        PlatformAccount account;
        try {
            if (Long.parseLong(parts[1]) <= Instant.now().getEpochSecond()) throw invalido();
            account = instalado(Long.parseLong(parts[0]));
        } catch (NumberFormatException ex) {
            throw invalido();
        }
        if (!equal(signature(parts[0] + "." + parts[1], account), parts[2])
                || !account.getWidgetOrigin().equals(origin)) throw invalido();
        verificarPlano(account);
        if (days != 1 && days != 7 && days != 30)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha 1, 7 ou 30 dias.");
        var filter = switch (channel == null ? "" : channel) {
            case "todos" -> null;
            case "whatsapp" -> CanalComunicacao.WHATSAPP;
            case "gmail" -> CanalComunicacao.EMAIL;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Canal inválido.");
        };
        var today = LocalDate.now();
        var result = history.history(account.getId(), today.minusDays(days - 1L), today, filter);
        return new Resumo(account.getBrandName(), result.total(), result.accepted(), result.pending(), result.failed());
    }

    private void verificarTitular() {
        var role = platform.currentUser().getTipo();
        if (role != TipoUsuario.CLIENTE && role != TipoUsuario.ADMINISTRADOR)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente o titular pode gerenciar o widget.");
    }

    private void verificarPlano(PlatformAccount account) {
        if (account.isSuspended() || !plans.pro(account))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "O widget exige um plano Profissional ativo.");
    }

    private PlatformAccount instalado(Long id) {
        if (id == null || id <= 0) throw invalido();
        return accounts.findById(id).filter(a -> a.getWidgetOrigin() != null && a.getWidgetKeyDigest() != null)
                .orElseThrow(WidgetParceiroService::invalido);
    }

    private static ResponseStatusException invalido() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Acesso ao widget inválido ou expirado.");
    }

    private static String origem(String value) {
        try {
            var uri = URI.create(value == null ? "" : value.strip());
            String host = uri.getHost();
            if (host != null && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && (uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
                var scheme = uri.getScheme().toLowerCase(Locale.ROOT);
                host = host.toLowerCase(Locale.ROOT);
                boolean local = "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
                if ("https".equals(scheme) || ("http".equals(scheme) && local)) {
                    int port = uri.getPort();
                    String origin = scheme + "://" + host;
                    if (port != -1 && port != ("https".equals(scheme) ? 443 : 80)) origin += ":" + port;
                    if (origin.length() <= 255 && port <= 65535 && port != 0) return origin;
                }
            }
        } catch (IllegalArgumentException ex) {
            // Return the same validation message for invalid and unsupported origins.
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a origem HTTPS do sistema parceiro.");
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String signature(String claims, PlatformAccount account) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            String data = "systemx-widget:v2:" + account.getWidgetKeyDigest() + ":" + account.getWidgetOrigin() + ":" + claims;
            return BASE64.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static boolean equal(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
