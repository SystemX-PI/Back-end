package com.systemX.senai.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.systemX.senai.dto.AssistenteMensagemRequest;
import com.systemX.senai.dto.AssistenteResposta;
import com.systemX.senai.dto.ComunicacaoResponse;
import com.systemX.senai.service.AssistenteService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/assistente")
public class AssistenteController {
    private final AssistenteService service;

    public AssistenteController(AssistenteService service) { this.service = service; }

    @PostMapping("/mensagens")
    public AssistenteResposta conversar(@Valid @RequestBody AssistenteMensagemRequest request) {
        return service.conversar(request.mensagem());
    }

    @PostMapping("/acoes/{token}/confirmar")
    public ComunicacaoResponse confirmar(@PathVariable String token) {
        return service.confirmarNotificacao(token);
    }
}
