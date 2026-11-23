package com.systemX.senai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "Autenticação", description = "Cadastro, login, vínculo com Google, renovação de sessão e recuperação de senha.")
public class AutenticacaoController {
    private final AuthService service;
    private final com.systemX.senai.service.PasswordResetService recovery;
    private final com.systemX.senai.config.PasswordResetOrigin resetOrigin;
    private final com.systemX.senai.service.UsuarioService usuarios;

    public AutenticacaoController(AuthService service, com.systemX.senai.service.UsuarioService usuarios,com.systemX.senai.service.PasswordResetService recovery, com.systemX.senai.config.PasswordResetOrigin resetOrigin) {
        this.service = service;
        this.recovery = recovery;
        this.resetOrigin = resetOrigin;
        this.usuarios = usuarios;
    }

    @Operation(summary = "Cadastrar uma conta por e-mail")
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<com.systemX.senai.dto.UsuarioResponse> registrar(
            @Valid @RequestBody com.systemX.senai.dto.RegistroRequest request) {
        return ResponseEntity.status(201).body(usuarios.registrar(request));
    }

    @Operation(summary = "Entrar com e-mail e senha")
    @SecurityRequirements
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    public record GoogleCredential(@NotBlank @Size(max=16384) String credential) { }

    public record GoogleRegistration(
            @NotBlank @Size(max=16384) String credential,
            @NotBlank @Size(min=2, max=255) String companyName) { }

    @Operation(summary = "Entrar com Google")
    @SecurityRequirements
    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleCredential request) {
        return service.loginWithGoogle(request.credential());
    }

    @Operation(summary = "Cadastrar uma conta com Google")
    @SecurityRequirements
    @PostMapping("/google/register")
    public AuthResponse registerWithGoogle(@Valid @RequestBody GoogleRegistration request) {
        return service.registerWithGoogle(request.credential(), request.companyName());
    }

    @Operation(summary = "Vincular Google à sessão atual")
    @PostMapping("/google/link")
    public ResponseEntity<Void> linkGoogle(@Valid @RequestBody GoogleCredential request, Principal principal) {
        service.linkGoogle(request.credential(), principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/google/photo")
    public java.util.Map<String, String> importGooglePhoto(@Valid @RequestBody GoogleCredential request,
            Principal principal) {
        return java.util.Map.of("photo", service.importGooglePhoto(request.credential(), principal.getName()));
    }

    @Operation(summary = "Renovar a sessão com o token de renovação")
    @SecurityRequirements
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return service.refresh(request.refreshToken());
    }

    @Operation(summary = "Encerrar a sessão")
    @SecurityRequirements
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        service.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
    public record ForgotPassword(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max=255) String email) {}
    public record ResetPassword(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max=255) String email,@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp="\\d{8}") String token,@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min=10,max=72) String newPassword) {}
    @Operation(summary = "Solicitar código de recuperação")
    @SecurityRequirements
    @PostMapping("/forgot-password") public Object forgot(@Valid @RequestBody ForgotPassword body,jakarta.servlet.http.HttpServletRequest request) {
        recovery.request(body.email(),resetOrigin.resolve(request));
        return java.util.Map.of("message","Se este e-mail estiver cadastrado, você receberá um código de recuperação.","expiresIn",300);
    }
    @Operation(summary = "Redefinir senha com o código de recuperação")
    @SecurityRequirements
    @PostMapping("/reset-password") public ResponseEntity<Void> reset(@Valid @RequestBody ResetPassword body,jakarta.servlet.http.HttpServletRequest request) {
        recovery.reset(body.email(),body.token(),body.newPassword(),resetOrigin.resolve(request));return ResponseEntity.noContent().build();
    }
}
