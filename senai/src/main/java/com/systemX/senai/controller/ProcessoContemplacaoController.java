package com.systemX.senai.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.systemX.senai.dto.PaginaResponse;
import com.systemX.senai.dto.ProcessoResponse;
import com.systemX.senai.enums.StatusProcesso;
import com.systemX.senai.dto.StatusProcessoRequest;
import com.systemX.senai.service.ProcessoContemplacaoService;

import jakarta.validation.Valid;

@RestController @RequestMapping("/api/processos-contemplacao")
@Tag(name = "Processos de contemplação", description = "Consulta de processos e atualização de status.")
public class ProcessoContemplacaoController {
    private final ProcessoContemplacaoService service;
    public ProcessoContemplacaoController(ProcessoContemplacaoService service) { this.service = service; }
    @GetMapping("/pagina")
    public PaginaResponse<ProcessoResponse> pagina(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "status", required = false) StatusProcesso status) {
        return service.pagina(page, size, q, status);
    }

    @GetMapping("/{id}") public ProcessoResponse buscar(@PathVariable("id") Long id) { return service.buscar(id); }
    @PatchMapping("/{id}/status") public ProcessoResponse atualizarStatus(@PathVariable("id") Long id, @Valid @RequestBody StatusProcessoRequest request) { return service.atualizarStatus(id, request); }
}