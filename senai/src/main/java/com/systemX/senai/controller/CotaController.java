package com.systemX.senai.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.systemX.senai.dto.CotaRequest;
import com.systemX.senai.dto.CotaResponse;
import com.systemX.senai.service.CotaService;

import jakarta.validation.Valid;

@RestController @RequestMapping("/api/cotas")
public class CotaController {
    private final CotaService service;
    public CotaController(CotaService service) { this.service = service; }
    @PostMapping public ResponseEntity<CotaResponse> criar(@Valid @RequestBody CotaRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(request)); }
    @GetMapping public List<CotaResponse> listar() { return service.listar(); }
}