package com.systemX.senai.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

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

import com.systemX.senai.dto.GrupoRequest;
import com.systemX.senai.dto.GrupoResponse;
import com.systemX.senai.service.GrupoService;

import jakarta.validation.Valid;

@RestController @RequestMapping("/api/grupos")
@Tag(name = "Grupos", description = "Cadastro e consulta de grupos de consórcio.")
public class GrupoController {
    private final GrupoService service;
    public GrupoController(GrupoService service) { this.service = service; }
    @PostMapping public ResponseEntity<GrupoResponse> criar(@Valid @RequestBody GrupoRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(request)); }
    @GetMapping public List<GrupoResponse> listar() { return service.listar(); }
    @GetMapping("/pagina")
    public com.systemX.senai.dto.PaginaResponse<GrupoResponse> pagina(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="20") int size,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="") String q) {
        return service.listarPagina(page, size, q);
    }
    @GetMapping("/{id}")
    public GrupoResponse consultar(@PathVariable Long id) { return service.consultar(id); }
    @PutMapping("/{id}")
    public GrupoResponse atualizar(@PathVariable Long id, @Valid @RequestBody GrupoRequest request) { return service.atualizar(id, request); }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) { service.excluir(id); return ResponseEntity.noContent().build(); }
}