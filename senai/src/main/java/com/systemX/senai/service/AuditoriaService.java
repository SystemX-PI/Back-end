package com.systemX.senai.service;

import com.systemX.senai.dto.PaginaResponse;
import com.systemX.senai.entity.Auditoria;
import com.systemX.senai.entity.Usuario;
import com.systemX.senai.repository.AuditoriaRepository;
import com.systemX.senai.repository.UsuarioRepository;
import jakarta.persistence.criteria.JoinType;
import java.time.LocalDateTime;
import org.springframework.security.core.context.SecurityContextHolder;
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
            String entidade, Long registroId, String observacao) {}

    public Usuario responsavel() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : usuarios.findByEmail(auth.getName()).orElse(null);
    }

    @Transactional
    public void registrar(String acao, String entidade, Long registroId, String detalhes) {
        auditorias.save(Auditoria.builder().usuario(responsavel()).acao(acao)
                .entidade(entidade).registroId(registroId).detalhes(detalhes).build());
    }

    @Transactional(readOnly = true)
    public PaginaResponse<Evento> pagina(int page, int size, String q) {
        var filtro = Paginacao.<Auditoria>busca(q, "acao", "entidade", "detalhes");
        var result = auditorias.findAll((root, query, cb) -> {
            if (query != null && query.getResultType() == Auditoria.class) root.fetch("usuario", JoinType.LEFT);
            return filtro.toPredicate(root, query, cb);
        }, Paginacao.pagina(page, size));
        return PaginaResponse.from(result.map(item -> new Evento(item.getId(), item.getData(), item.getAcao(),
                item.getUsuario() == null ? null : item.getUsuario().getNome(), item.getEntidade(),
                item.getRegistroId(), item.getDetalhes())));
    }
}
