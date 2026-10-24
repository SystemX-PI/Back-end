package com.systemX.senai.controller;

import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.systemX.senai.dto.ConsorcioRequest;
import com.systemX.senai.dto.ConsorcioResponse;
import com.systemX.senai.service.ConsorcioService;

import jakarta.validation.Valid;

@RestController @RequestMapping("/api/consorcios")
public class ConsorcioController {
    private final ConsorcioService service;
    public ConsorcioController(ConsorcioService service) { this.service = service; }
    @PostMapping public ResponseEntity<ConsorcioResponse> criar(@Valid @RequestBody ConsorcioRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(request)); }
    @GetMapping public List<ConsorcioResponse> listar() { return service.listar(); }
    @GetMapping("/pagina")
    public com.systemX.senai.dto.PaginaResponse<ConsorcioResponse> pagina(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="20") int size,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="") String q) {
        return service.listarPagina(page, size, q);
    }
    @GetMapping("/{id}")
    public ConsorcioResponse consultar(@PathVariable Long id) { return service.consultar(id); }
    @PutMapping("/{id}")
    public ConsorcioResponse atualizar(@PathVariable Long id, @Valid @RequestBody ConsorcioRequest request) { return service.atualizar(id, request); }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) { service.excluir(id); return ResponseEntity.noContent().build(); }
}