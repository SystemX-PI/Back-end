package com.systemX.senai.controller;

import com.systemX.senai.enums.TipoUsuario;
import com.systemX.senai.service.PlatformService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/platform/team-groups")
public class GruposEquipeController {
    private final JdbcTemplate db;
    private final PlatformService platform;

    public GruposEquipeController(JdbcTemplate db, PlatformService platform) {
        this.db = db;
        this.platform = platform;
    }

    public record Grupo(Long id, String nome, List<Long> usuarios, boolean permiteDisparo) {}
    public record Dados(@NotBlank @Size(max=100) String nome,
            @NotNull @Size(max=1000) List<@NotNull @Positive Long> usuarios, boolean permiteDisparo) {}

    @GetMapping
    public List<Grupo> listar(@RequestParam(required=false) Long accountId) {
        long conta = platform.account(accountId).getId();
        return db.query("SELECT id,nome,permite_disparo FROM equipe_grupo WHERE conta_id=? ORDER BY nome,id",
                (rs, row) -> new Grupo(rs.getLong(1), rs.getString(2), List.of(), rs.getBoolean(3)), conta)
                .stream().map(g -> new Grupo(g.id(), g.nome(), db.queryForList("""
                    SELECT m.usuario_id FROM equipe_grupo_usuario m
                    JOIN usuario u ON u.id=m.usuario_id
                    WHERE m.grupo_id=? AND u.conta_id=? AND u.tipo='USUARIO' ORDER BY m.usuario_id
                    """, Long.class, g.id(), conta), g.permiteDisparo())).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public void criar(@RequestParam(required=false) Long accountId, @Valid @RequestBody Dados dados) {
        long conta = gerenciar(accountId);
        String nome = dados.nome().strip();
        try {
            db.update("INSERT INTO equipe_grupo (conta_id,nome,permite_disparo) VALUES (?,?,?)", conta, nome, dados.permiteDisparo());
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um grupo com esse nome.");
        }
        Long id = db.queryForObject("SELECT id FROM equipe_grupo WHERE conta_id=? AND nome=?", Long.class, conta, nome);
        membros(conta, id, dados.usuarios());
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void editar(@RequestParam(required=false) Long accountId, @PathVariable long id, @Valid @RequestBody Dados dados) {
        long conta = gerenciar(accountId);
        if (db.queryForList("SELECT id FROM equipe_grupo WHERE id=? AND conta_id=?", Long.class, id, conta).isEmpty())
            throw inexistente();
        try {
            db.update("UPDATE equipe_grupo SET nome=?,permite_disparo=? WHERE id=? AND conta_id=?", dados.nome().strip(), dados.permiteDisparo(), id, conta);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um grupo com esse nome.");
        }
        db.update("DELETE FROM equipe_grupo_usuario WHERE grupo_id=?", id);
        membros(conta, id, dados.usuarios());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void excluir(@RequestParam(required=false) Long accountId, @PathVariable long id) {
        if (db.update("DELETE FROM equipe_grupo WHERE id=? AND conta_id=?", id, gerenciar(accountId)) == 0)
            throw inexistente();
    }

    private long gerenciar(Long accountId) {
        var tipo = platform.currentUser().getTipo();
        if (tipo != TipoUsuario.CLIENTE && tipo != TipoUsuario.ADMINISTRADOR)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente o titular pode gerenciar grupos.");
        return platform.lockAccount(accountId).getId();
    }

    private void membros(long conta, long grupo, List<Long> usuarios) {
        for (long usuario : usuarios.stream().distinct().toList()) {
            int inserted = db.update("""
                INSERT INTO equipe_grupo_usuario (grupo_id,usuario_id)
                SELECT ?,id FROM usuario WHERE id=? AND conta_id=? AND tipo='USUARIO'
                """, grupo, usuario, conta);
            if (inserted == 0)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione usuários desta equipe.");
        }
    }

    private ResponseStatusException inexistente() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo não encontrado.");
    }
}
