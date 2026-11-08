package com.systemX.senai.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.systemX.senai.dto.EmailConfiguracaoRequest;
import com.systemX.senai.dto.EmailConfiguracaoResponse;
import com.systemX.senai.service.EmailService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/configuracoes")
@Tag(name = "Configuração de e-mail", description = "Remetente usado pelas comunicações da operação.")
public class ConfiguracaoController {
    private final EmailService emailService;

    public ConfiguracaoController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PutMapping("/email")
    public ResponseEntity<EmailConfiguracaoResponse> configurarEmail(
            @Valid @RequestBody EmailConfiguracaoRequest request) {
        emailService.configure(request.email(), request.senhaAplicativo());
        return ResponseEntity.ok(new EmailConfiguracaoResponse(true, request.email().trim()));
    }
}