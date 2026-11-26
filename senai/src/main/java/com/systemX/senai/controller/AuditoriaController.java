package com.systemX.senai.controller;

import com.systemX.senai.dto.PaginaResponse;
import com.systemX.senai.service.AuditoriaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auditoria")
@Tag(name = "Auditoria")
public class AuditoriaController {
    private final AuditoriaService auditoria;

    public AuditoriaController(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    @GetMapping("/pagina")
    public PaginaResponse<AuditoriaService.Evento> pagina(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String q,
            @RequestParam(required = false) String acao,
            @RequestParam(required = false) java.time.LocalDate data,
            @RequestParam(required = false) Long contaId) {
        return auditoria.pagina(page, size, q, acao, data, contaId, false);
    }
}
