package com.systemX.senai.controller;

import com.systemX.senai.service.CanaisOperacaoService;
import com.systemX.senai.service.PlatformService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/operation-channels")
@Tag(name = "Canais internos")
public class CanaisOperacaoController {
    private final PlatformService platform;
    private final CanaisOperacaoService canais;

    public CanaisOperacaoController(PlatformService platform, CanaisOperacaoService canais) {
        this.platform = platform;
        this.canais = canais;
    }

    public record WhatsAppTeste(@NotNull @Positive Long senderId, @NotBlank @Pattern(regexp = "[+]?[0-9]{8,15}") String recipient,
            @NotBlank @Size(max = 2000) String message) {}
    public record GmailTeste(@NotNull @Positive Long senderId, @NotBlank @Email @Size(max = 255) String recipient,
            @NotBlank @Size(max = 160) String subject, @NotBlank @Size(max = 2000) String message) {}

    @GetMapping
    public Object canais() { return platform.internalChannels(); }

    @PostMapping("/test/whatsapp")
    public CanaisOperacaoService.Resultado whatsapp(@Valid @RequestBody WhatsAppTeste envio) {
        return canais.whatsapp(envio.senderId(), envio.recipient(), envio.message());
    }

    @PostMapping("/test/gmail")
    public CanaisOperacaoService.Resultado gmail(@Valid @RequestBody GmailTeste envio) {
        return canais.gmail(envio.senderId(), envio.recipient(), envio.subject(), envio.message());
    }
}
