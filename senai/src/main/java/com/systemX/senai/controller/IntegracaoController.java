package com.systemX.senai.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.systemX.senai.dto.BaixaRequest;
import com.systemX.senai.dto.BaixaResponse;
import com.systemX.senai.dto.ComunicacaoResponse;
import com.systemX.senai.dto.ContempladoRequest;
import com.systemX.senai.dto.IntegracaoResponse;
import com.systemX.senai.dto.FilaComunicacaoResponse;
import com.systemX.senai.dto.WhatsAppRequest;
import com.systemX.senai.service.BaixaService;
import com.systemX.senai.service.IntegracaoContempladoService;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/integracoes")
public class IntegracaoController {
    private final IntegracaoContempladoService integracaoService;
    private final BaixaService baixaService;
    private final String integrationKey;

    public IntegracaoController(IntegracaoContempladoService integracaoService, BaixaService baixaService,
            @Value("${app.integration.api-key:}") String integrationKey) {
        this.integracaoService = integracaoService;
        this.baixaService = baixaService;
        this.integrationKey = integrationKey;
    }

    @PostMapping("/contemplacoes")
    public ResponseEntity<IntegracaoResponse> receber(@RequestHeader("X-Integration-Key") String key,
            @Valid @RequestBody ContempladoRequest request) {
        validarChave(key);
        return ResponseEntity.status(HttpStatus.CREATED).body(integracaoService.receber(request));
    }

    @GetMapping("/processos/{processoId}/comunicacao")
    public ComunicacaoResponse comunicacao(@PathVariable Long processoId) {
        return integracaoService.consultar(processoId);
    }

    @GetMapping("/comunicacoes/fila")
    public List<FilaComunicacaoResponse> fila() {
        return integracaoService.listarFila();
    }

    @GetMapping("/comunicacoes/fila/pagina")
    public com.systemX.senai.dto.PaginaResponse<FilaComunicacaoResponse> filaPagina(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="20") int size,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="") String q,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="") String status) {
        return integracaoService.listarFilaPagina(page, size, q, status);
    }

    @PostMapping("/processos/{processoId}/comunicacao/reenvio")
    public ComunicacaoResponse reenviar(@PathVariable Long processoId) {
        return integracaoService.reenviar(processoId);
    }

    @PostMapping("/processos/{processoId}/comunicacao/whatsapp")
    public ComunicacaoResponse whatsapp(@PathVariable Long processoId,
            @Valid @RequestBody WhatsAppRequest request) {
        return integracaoService.enviarWhatsApp(processoId, request);
    }

    @PostMapping("/processos/{processoId}/baixa")
    public BaixaResponse baixa(@PathVariable Long processoId, @Valid @RequestBody BaixaRequest request) {
        return baixaService.criar(processoId, request);
    }

    @GetMapping("/processos/{processoId}/baixa")
    public BaixaResponse consultarBaixa(@PathVariable Long processoId) {
        return baixaService.consultar(processoId);
    }

    private void validarChave(String key) {
        if (integrationKey.isBlank() || !integrationKey.equals(key)) {
            throw new org.springframework.security.access.AccessDeniedException("Chave de integração inválida");
        }
    }
}
