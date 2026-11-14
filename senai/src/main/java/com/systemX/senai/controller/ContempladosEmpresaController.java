package com.systemX.senai.controller;

import com.systemX.senai.service.*;
import com.systemX.senai.repository.PlatformMailboxRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
public class ContempladosEmpresaController {
    private final JdbcTemplate db;
    private final PlatformService platform;
    private final PlatformMailboxRepository mailboxes;
    private final PlatformCredentialService gmail;
    private final WhatsAppService whatsapp;

    public ContempladosEmpresaController(JdbcTemplate db, PlatformService platform,
            PlatformMailboxRepository mailboxes, PlatformCredentialService gmail, WhatsAppService whatsapp) {
        this.db = db;
        this.platform = platform;
        this.mailboxes = mailboxes;
        this.gmail = gmail;
        this.whatsapp = whatsapp;
    }

    public record Entrada(@NotBlank @Size(max=100) String origem, @NotBlank @Size(max=150) String referencia,
            @NotBlank @Size(max=150) String nome, @Pattern(regexp="[+0-9(). -]{8,30}") String telefone,
            @Email @Size(max=255) String email) {}
    public record Envio(@Pattern(regexp="WHATSAPP|GMAIL") @NotBlank String canal,
            @NotNull @Positive Long remetente, @NotBlank @Size(max=2000) String mensagem) {}
    public record Manual(@NotBlank @Pattern(regexp="WHATSAPP|GMAIL") String canal,
            @NotNull @Positive Long remetente, @NotBlank @Size(max=2000) String mensagem,
            @NotBlank @Size(max=255) String destino) {}

    @GetMapping("/api/platform/disparo-manual")
    public Map<String,Boolean> acessoDisparo() {
        return Map.of("permitido", podeDisparar(platform.account(null).getId()));
    }

    @PostMapping("/api/platform/disparo-manual")
    public Map<String,String> manual(@Valid @RequestBody Manual pedido) {
        var conta = platform.contaParaEnvio(null);
        exigirDisparo(conta.getId());
        String destino = pedido.destino().strip();
        boolean valido = "WHATSAPP".equals(pedido.canal()) ? destino.matches("[+]?[0-9]{8,15}")
                : destino.matches("[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+");
        if (!valido) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confira o destinatário.");
        String estado = entregar(conta, new Envio(pedido.canal(), pedido.remetente(), pedido.mensagem()), destino);
        db.update("INSERT INTO disparo_manual_empresa (conta_id,usuario_id,canal,destino,estado) VALUES (?,?,?,?,?)",
                conta.getId(), platform.currentUser().getId(), pedido.canal(), destino, estado);
        return Map.of("estado", estado);
    }

    private boolean podeDisparar(long conta) {
        var usuario = platform.currentUser();
        if (usuario.getTipo() == com.systemX.senai.enums.TipoUsuario.CLIENTE) return true;
        Integer grupos = db.queryForObject("""
            SELECT COUNT(*) FROM equipe_grupo_usuario m JOIN equipe_grupo g ON g.id=m.grupo_id
            WHERE m.usuario_id=? AND g.conta_id=? AND g.permite_disparo=TRUE
            """, Integer.class, usuario.getId(), conta);
        return grupos != null && grupos > 0;
    }

    private void exigirDisparo(long conta) {
        if (!podeDisparar(conta)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Seu grupo não tem permissão para enviar mensagens.");
    }

    @PostMapping("/api/platform/contemplados/chave")
    public Map<String,String> chave() {
        if (platform.currentUser().getTipo() != com.systemX.senai.enums.TipoUsuario.CLIENTE)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        long conta = platform.account(null).getId();
        String chave = UUID.randomUUID().toString() + UUID.randomUUID();
        db.update("UPDATE platform_account SET contemplados_chave_hash=? WHERE id=?", hash(chave), conta);
        return Map.of("chave", chave);
    }

    @PostMapping("/api/integracoes/contemplados-empresa")
    public void receber(@RequestHeader("X-Integration-Key") String chave, @Valid @RequestBody Entrada entrada) {
        var contas = db.queryForList("SELECT id FROM platform_account WHERE contemplados_chave_hash=? AND suspended=FALSE",
                Long.class, hash(chave));
        if (contas.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chave inválida.");
        try {
            db.update("INSERT INTO contemplado_empresa (conta_id,origem,referencia,nome,telefone,email) VALUES (?,?,?,?,?,?)",
                    contas.get(0), entrada.origem().trim(), entrada.referencia().trim(), entrada.nome().trim(),
                    entrada.telefone(), entrada.email());
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            // A referência já foi recebida desta origem para a conta.
        }
    }

    @GetMapping("/api/platform/contemplados")
    public List<Map<String,Object>> fila() {
        return db.queryForList("""
            SELECT c.id,c.nome,c.origem,c.referencia,c.telefone,c.email,c.recebido_em,
             (SELECT estado FROM contemplado_envio WHERE contemplado_id=c.id AND canal='WHATSAPP' ORDER BY id DESC LIMIT 1) whatsapp,
             (SELECT estado FROM contemplado_envio WHERE contemplado_id=c.id AND canal='GMAIL' ORDER BY id DESC LIMIT 1) gmail
            FROM contemplado_empresa c WHERE c.conta_id=? ORDER BY c.id DESC LIMIT 100
            """, platform.account(null).getId());
    }

    @GetMapping("/api/platform/contemplados/{id}/envios")
    public List<Map<String,Object>> historico(@PathVariable long id) {
        registro(platform.account(null).getId(), id);
        return db.queryForList("SELECT canal,estado,criado_em FROM contemplado_envio WHERE contemplado_id=? ORDER BY id DESC LIMIT 50", id);
    }

    @PostMapping("/api/platform/contemplados/{id}/envios")
    public Map<String,String> enviar(@PathVariable long id, @Valid @RequestBody Envio envio) {
        var conta = platform.contaParaEnvio(null);
        exigirDisparo(conta.getId());
        var pessoa = registro(conta.getId(), id);
        boolean porWhatsApp = "WHATSAPP".equals(envio.canal());
        var destino = (String) pessoa.get(porWhatsApp ? "telefone" : "email");
        if (destino == null || destino.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O contemplado não tem contato para esse canal.");
        String estado = entregar(conta, envio, destino);
        db.update("INSERT INTO contemplado_envio (contemplado_id,canal,estado) VALUES (?,?,?)", id, envio.canal(), estado);
        return Map.of("estado", estado);
    }

    private String entregar(com.systemX.senai.entity.PlatformAccount conta, Envio envio, String destino) {
        Runnable enviar;
        if ("WHATSAPP".equals(envio.canal())) {
            String remoto = platform.remetenteWhatsApp(conta, envio.remetente());
            enviar = () -> whatsapp.sendFrom(remoto, destino, envio.mensagem());
        } else {
            var caixa = mailboxes.findByIdAndContaId(envio.remetente(), conta.getId())
                    .filter(m -> m.isVerified()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            enviar = () -> gmail.sendGmail(caixa.getEmail(), caixa.getEncryptedPassword(), destino,
                    "Aviso sobre sua contemplação", envio.mensagem());
        }
        String estado;
        try {
            enviar.run();
            estado = "ACEITO";
        } catch (RuntimeException failure) {
            estado = "FALHA";
        }
        return estado;
    }

    private Map<String,Object> registro(long conta, long id) {
        var rows = db.queryForList("SELECT telefone,email FROM contemplado_empresa WHERE conta_id=? AND id=?", conta, id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return rows.get(0);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
