package com.systemX.senai.controller;

import java.util.List;

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
}