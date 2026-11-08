package com.systemX.senai.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import com.systemX.senai.dto.*;
import com.systemX.senai.service.PortalService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/portal/processos")
@Tag(name = "Portal do cliente", description = "Consulta dos processos do cliente autenticado.")
public class PortalController {
    private final PortalService service;
    public PortalController(PortalService service) { this.service = service; }
    @GetMapping
    public PaginaResponse<ProcessoResponse> listar(Principal user, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return service.listar(user.getName(), page, size); }
    @GetMapping("/{id}")
    public PortalProcessoResponse consultar(@PathVariable Long id, Principal user) { return service.consultar(id, user.getName()); }
}
