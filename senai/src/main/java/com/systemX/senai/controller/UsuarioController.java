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

import com.systemX.senai.dto.UsuarioRequest;
import com.systemX.senai.dto.UsuarioResponse;
import com.systemX.senai.service.UsuarioService;

import jakarta.validation.Valid;

@RestController @RequestMapping("/api/usuarios")
public class UsuarioController {
    private final UsuarioService service;
    public UsuarioController(UsuarioService service) { this.service = service; }
    @PostMapping public ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody UsuarioRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(request)); }
    @GetMapping public List<UsuarioResponse> listar() { return service.listar(); }
    @GetMapping("/pagina")
    public com.systemX.senai.dto.PaginaResponse<UsuarioResponse> pagina(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="20") int size,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue="") String q, @org.springframework.web.bind.annotation.RequestParam(required=false) com.systemX.senai.enums.TipoUsuario tipo) {
        return service.listarPagina(page, size, q, tipo);
    }
    @GetMapping("/{id}")
    public UsuarioResponse consultar(@PathVariable Long id) { return service.consultar(id); }
    @PutMapping("/{id}")
    public UsuarioResponse atualizar(@PathVariable Long id, @Valid @RequestBody com.systemX.senai.dto.UsuarioAtualizacaoRequest request) { return service.atualizar(id, request); }
    @PutMapping("/{id}/senha")
    public ResponseEntity<Void> senha(@PathVariable Long id, @Valid @RequestBody com.systemX.senai.dto.SenhaRequest request) {
        service.alterarSenha(id, request); return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) { service.excluir(id); return ResponseEntity.noContent().build(); }
}