package com.systemX.senai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.security.Principal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.systemX.senai.dto.AuthResponse;
import com.systemX.senai.dto.LoginRequest;
import com.systemX.senai.dto.RefreshTokenRequest;
import com.systemX.senai.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final com.systemX.senai.service.PasswordResetService recovery;
    private final com.systemX.senai.service.UsuarioService usuarios;

    public AuthController(AuthService service, com.systemX.senai.service.UsuarioService usuarios,com.systemX.senai.service.PasswordResetService recovery) {
        this.service = service;
        this.recovery = recovery;
        this.usuarios = usuarios;
    }

    @PostMapping("/register")
    public ResponseEntity<com.systemX.senai.dto.UsuarioResponse> registrar(
            @Valid @RequestBody com.systemX.senai.dto.RegistroRequest request) {
        return ResponseEntity.status(201).body(usuarios.registrar(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    public record GoogleCredential(@NotBlank @Size(max=16384) String credential) { }

    public record GoogleRegistration(
            @NotBlank @Size(max=16384) String credential,
            @NotBlank @Size(min=2, max=255) String companyName) { }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleCredential request) {
        return service.loginWithGoogle(request.credential());
    }

    @PostMapping("/google/register")
    public AuthResponse registerWithGoogle(@Valid @RequestBody GoogleRegistration request) {
        return service.registerWithGoogle(request.credential(), request.companyName());
    }

    @PostMapping("/google/link")
    public ResponseEntity<Void> linkGoogle(@Valid @RequestBody GoogleCredential request, Principal principal) {
        service.linkGoogle(request.credential(), principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return service.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        service.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
    public record ForgotPassword(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max=255) String email) {}
    public record ResetPassword(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max=255) String email,@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp="\\d{8}") String token,@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min=10,max=72) String newPassword) {}
    @PostMapping("/forgot-password") public Object forgot(@Valid @RequestBody ForgotPassword body,jakarta.servlet.http.HttpServletRequest request) {
        recovery.request(body.email(),request.getRemoteAddr());
        return java.util.Map.of("message","Se este e-mail estiver cadastrado, você receberá um código de recuperação.","expiresIn",300);
    }
    @PostMapping("/reset-password") public ResponseEntity<Void> reset(@Valid @RequestBody ResetPassword body) {
        recovery.reset(body.email(),body.token(),body.newPassword());return ResponseEntity.noContent().build();
    }
}
