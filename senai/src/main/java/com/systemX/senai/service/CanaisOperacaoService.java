package com.systemX.senai.service;

import com.systemX.senai.repository.PlatformMailboxRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CanaisOperacaoService {
    private final PlatformService platform;
    private final PlatformMailboxRepository mailboxes;
    private final WhatsAppService whatsapp;
    private final PlatformCredentialService credentials;
    private final AuditoriaService auditoria;

    public CanaisOperacaoService(PlatformService platform, PlatformMailboxRepository mailboxes,
            WhatsAppService whatsapp, PlatformCredentialService credentials,
            AuditoriaService auditoria) {
        this.platform = platform;
        this.mailboxes = mailboxes;
        this.whatsapp = whatsapp;
        this.credentials = credentials;
        this.auditoria = auditoria;
    }

    public record Resultado(boolean accepted, String message) {}

    public Resultado whatsapp(Long id, String recipient, String text) {
        var sender = platform.connectedInternalWhatsApp(id);
        return enviar("WhatsApp", id, () -> whatsapp.sendFrom(sender.getRemoteName(), recipient, text));
    }

    public Resultado gmail(Long id, String recipient, String subject, String text) {
        var sender = mailboxes.findByIdAndContaId(id, platform.internalAccount().getId())
                .filter(mailbox -> mailbox.isVerified())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gmail interno não encontrado."));
        return enviar("Gmail", id, () -> credentials.sendGmail(sender.getEmail(), sender.getEncryptedPassword(), recipient, subject, text));
    }

    private Resultado enviar(String canal, Long id, Runnable action) {
        boolean aceito;
        String message;
        try {
            action.run();
            aceito = true;
            message = "Envio aceito pelo provedor.";
        } catch (ResponseStatusException ex) {
            aceito = false;
            message = ex.getReason();
        } catch (RuntimeException ex) {
            aceito = false;
            message = "O envio falhou. Confira a conexão do remetente e o destinatário.";
        }
        auditoria.registrar("Envio de teste · " + canal, "Canal interno", id, aceito ? "Aceito pelo provedor" : "Falha no envio");
        return new Resultado(aceito, message);
    }
}
