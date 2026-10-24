package com.systemX.senai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.systemX.senai.dto.AuthResponse;
import com.systemX.senai.dto.LoginRequest;
import com.systemX.senai.dto.RefreshTokenRequest;
import com.systemX.senai.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final com.systemX.senai.service.UsuarioService usuarios;

    public AuthController(AuthService service, com.systemX.senai.service.UsuarioService usuarios) {
        this.service = service;
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

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return service.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        service.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}