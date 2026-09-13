package com.systemX.senai.controller;

import java.util.List;

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
}