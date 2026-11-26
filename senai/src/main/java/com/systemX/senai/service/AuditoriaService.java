package com.systemX.senai.service;

import com.systemX.senai.dto.PaginaResponse;
import com.systemX.senai.entity.Auditoria;
import com.systemX.senai.entity.Usuario;
import com.systemX.senai.enums.TipoUsuario;
import com.systemX.senai.repository.AuditoriaRepository;
import com.systemX.senai.repository.UsuarioRepository;
import jakarta.persistence.criteria.JoinType;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditoriaService {
    private final AuditoriaRepository auditorias;
    private final UsuarioRepository usuarios;

    public AuditoriaService(AuditoriaRepository auditorias, UsuarioRepository usuarios) {
        this.auditorias = auditorias;
        this.usuarios = usuarios;
    }

    public record Evento(Long id, LocalDateTime data, String acao, String usuarioNome,
            String entidade, Long registroId, String observacao, Long contaId) {}

    public Usuario responsavel() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : usuarios.findByEmail(auth.getName()).orElse(null);
    }

    public Usuario exigirOperador() {
        Usuario usuario = responsavel();
        if (usuario == null || !usuario.isAtivo()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida.");
        }
        if (usuario.getTipo() != TipoUsuario.ADMINISTRADOR && usuario.getTipo() != TipoUsuario.FUNCIONARIO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operação restrita à equipe interna.");
        }
        return usuario;
    }

    public Usuario exigirOperadorGlobal() {
        var operador = exigirOperador();
        if (operador.getTipo() != TipoUsuario.ADMINISTRADOR && operador.getContaId() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso exclusivo da operação interna.");
        }
        return operador;
    }

    @Transactional
    public void registrar(String acao, String entidade, Long registroId, String detalhes) {
        registrar(acao, entidade, registroId, detalhes, null);
    }

    @Transactional
    public void registrar(String acao, String entidade, Long registroId, String detalhes, Long contaId) {
        auditorias.save(Auditoria.builder().usuario(responsavel()).acao(acao)
                .entidade(entidade).registroId(registroId).detalhes(detalhes).contaId(contaId).build());
    }

    @Transactional(readOnly = true)
    public PaginaResponse<Evento> pagina(int page, int size, String q, String acao, java.time.LocalDate data) {
        return pagina(page, size, q, acao, data, null, false);
    }

    @Transactional(readOnly = true)
    public PaginaResponse<Evento> pagina(int page, int size, String q, String acao, java.time.LocalDate data,
            Long contaId, boolean apenasProcessos) {
        var filtro = Paginacao.<Auditoria>busca(q, "acao", "entidade", "detalhes");
        var result = auditorias.findAll((root, query, cb) -> {
            if (query != null && query.getResultType() == Auditoria.class) root.fetch("usuario", JoinType.LEFT);
            var predicate = filtro.toPredicate(root, query, cb);
            if (q != null && !q.isBlank()) {
                if (!apenasProcessos) {
                    var usuario = root.join("usuario", JoinType.LEFT);
                    var email = cb.equal(cb.lower(usuario.get("email")), q.trim().toLowerCase(java.util.Locale.ROOT));
                    predicate = cb.or(predicate, email);
                }
                try {
                    predicate = cb.or(predicate, cb.equal(root.get("registroId"), Long.parseLong(q.trim())));
                } catch (NumberFormatException ignored) {
                    // Text searches still match action, entity, details and operator email.
                }
            }
            if (acao != null && !acao.isBlank()) predicate = cb.and(predicate, cb.equal(root.get("acao"), acao));
            if (contaId != null) predicate = cb.and(predicate, cb.equal(root.get("contaId"), contaId));
            if (apenasProcessos) predicate = cb.and(predicate, cb.equal(root.get("entidade"), "Processo"));
            if (data != null) predicate = cb.and(predicate,
                    cb.greaterThanOrEqualTo(root.get("data"), data.atStartOfDay()),
                    cb.lessThan(root.get("data"), data.plusDays(1).atStartOfDay()));
            return predicate;
        }, Paginacao.pagina(page, size));
        return PaginaResponse.from(result.map(item -> new Evento(item.getId(), item.getData(), item.getAcao(),
                item.getUsuario() == null ? null : item.getUsuario().getNome(), item.getEntidade(),
                item.getRegistroId(), item.getDetalhes(), item.getContaId())));
    }
}
