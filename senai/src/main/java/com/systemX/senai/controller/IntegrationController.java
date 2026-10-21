package com.systemX.senai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.systemX.senai.dto.EmailConfigurationRequest;
import com.systemX.senai.dto.EmailConfigurationResponse;
import com.systemX.senai.dto.EvolutionQrRequest;
import com.systemX.senai.dto.EvolutionStatusResponse;
import com.systemX.senai.service.EmailConfigurationService;
import com.systemX.senai.service.EvolutionService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api")
public class IntegrationController {
    private final EmailConfigurationService emailConfigurationService;
    private final EvolutionService evolutionService;

    public IntegrationController(
            EmailConfigurationService emailConfigurationService,
            EvolutionService evolutionService) {
        this.emailConfigurationService = emailConfigurationService;
        this.evolutionService = evolutionService;
    }

    @PutMapping("/configuracoes/email")
    public ResponseEntity<EmailConfigurationResponse> configureEmail(
            @Valid @RequestBody EmailConfigurationRequest request) {
        return ResponseEntity.ok(emailConfigurationService.configure(request));
    }

    @PostMapping("/integracoes/whatsapp/qr")
    public ResponseEntity<EvolutionStatusResponse> generateWhatsAppQr(
            @Valid @RequestBody EvolutionQrRequest request) {
        return ResponseEntity.ok(evolutionService.generateQr(request.instanceName()));
    }

    @GetMapping("/integracoes/whatsapp/status")
    public ResponseEntity<EvolutionStatusResponse> getWhatsAppStatus(@RequestParam String instanceName) {
        return ResponseEntity.ok(evolutionService.getStatus(instanceName));
    }
}
