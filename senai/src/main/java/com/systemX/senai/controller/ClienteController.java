package com.systemX.senai.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.systemX.senai.dto.*;
import com.systemX.senai.enums.TipoUsuario;
import com.systemX.senai.service.UsuarioService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/clientes")
@Tag(name = "Clientes da operação", description = "Consulta de clientes dos processos de consórcio.")
public class ClienteController {
    private final UsuarioService service;
    public ClienteController(UsuarioService service) { this.service = service; }
    @GetMapping("/pagina")
    public PaginaResponse<UsuarioResponse> pagina(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="") String q) {
        return service.listarPagina(page, size, q, TipoUsuario.CLIENTE);
    }
}
