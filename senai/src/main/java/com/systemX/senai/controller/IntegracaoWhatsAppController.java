package com.systemX.senai.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.systemX.senai.dto.EvolutionQrRequest;
import com.systemX.senai.dto.EvolutionStatusResponse;
import com.systemX.senai.service.EvolutionService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api")
@Tag(name = "WhatsApp da operação", description = "QR e estado de instâncias usados pela integração de operações. Acesso da administração.")
public class IntegracaoWhatsAppController {
    private final EvolutionService evolutionService;

    public IntegracaoWhatsAppController(
            EvolutionService evolutionService) {
        this.evolutionService = evolutionService;
    }

    @Operation(summary = "Gerar QR de uma instância operacional")
    @PostMapping("/integracoes/whatsapp/qr")
    public ResponseEntity<EvolutionStatusResponse> generateWhatsAppQr(
            @Valid @RequestBody EvolutionQrRequest request) {
        return ResponseEntity.ok(evolutionService.generateQr(request.instanceName()));
    }

    @Operation(summary = "Consultar o estado de uma instância operacional")
    @GetMapping("/integracoes/whatsapp/status")
    public ResponseEntity<EvolutionStatusResponse> getWhatsAppStatus(@RequestParam String instanceName) {
        return ResponseEntity.ok(evolutionService.getStatus(instanceName));
    }
}
