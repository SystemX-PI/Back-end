package com.systemX.senai.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@Tag(name = "Navegação", description = "Entrada da documentação da API.")
public class DocumentacaoController {

    @GetMapping("/")
    public RedirectView abrirDocumentacao() {
        return new RedirectView("/swagger-ui/index.html");
    }
}