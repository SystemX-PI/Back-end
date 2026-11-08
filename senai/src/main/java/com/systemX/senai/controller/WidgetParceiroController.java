package com.systemX.senai.controller;

import com.systemX.senai.service.WidgetParceiroService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Widget parceiro")
public class WidgetParceiroController {
    private final WidgetParceiroService widget;

    public WidgetParceiroController(WidgetParceiroService widget) {
        this.widget = widget;
    }

    public record Configuracao(String origin) {}
    public record Sessao(Long accountId) {}
    public record Consulta(String origin, String channel, int days) {}

    @GetMapping("/api/platform/widget")
    public Object configuracao(@RequestParam(required = false) Long accountId) {
        return widget.configuracao(accountId);
    }

    @PutMapping("/api/platform/widget")
    public ResponseEntity<?> configurar(@RequestParam(required = false) Long accountId,
            @RequestBody Configuracao body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(widget.configurar(accountId, body.origin()));
    }

    @DeleteMapping("/api/platform/widget")
    public ResponseEntity<Void> desativar(@RequestParam(required = false) Long accountId) {
        widget.desativar(accountId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/widget/session")
    public ResponseEntity<?> sessao(@RequestHeader("X-Widget-Key") String key, @RequestBody Sessao body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(widget.sessao(body.accountId(), key));
    }

    @PostMapping("/api/widget/chat")
    public ResponseEntity<?> consultar(@RequestHeader("X-Widget-Session") String token, @RequestBody Consulta body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(widget.consultar(token, body.origin(), body.channel(), body.days()));
    }
}
