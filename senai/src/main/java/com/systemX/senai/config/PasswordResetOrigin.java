package com.systemX.senai.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetOrigin {
    private final Set<String> trustedProxies;

    public PasswordResetOrigin(@Value("${app.security.password-reset.trusted-proxies:}") String proxies) {
        this.trustedProxies = Arrays.stream(proxies.split(",")).map(String::strip)
                .map(PasswordResetOrigin::literalIp).filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(HttpServletRequest request) {
        String peer = literalIp(request.getRemoteAddr());
        if (peer == null) return "unknown";
        if (trustedProxies.contains(peer)) {
            String forwarded = literalIp(request.getHeader("X-Real-IP"));
            if (forwarded != null) return forwarded;
        }
        return peer;
    }

    private static String literalIp(String value) {
        if (value == null || value.length() > 45 || !(value.matches("[0-9]+(\\.[0-9]+){3}")
                || (value.contains(":") && value.matches("[0-9a-fA-F:.]+")))) return null;
        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
