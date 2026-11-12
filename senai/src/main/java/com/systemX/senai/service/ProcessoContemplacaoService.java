package com.systemX.senai.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import com.systemX.senai.dto.AtribuirResponsavelRequest;
import com.systemX.senai.dto.PaginaResponse;
import com.systemX.senai.dto.ProcessoResponse;
import com.systemX.senai.dto.StatusProcessoRequest;
import com.systemX.senai.entity.HistoricoProcesso;
import com.systemX.senai.entity.ProcessoContemplacao;
import com.systemX.senai.entity.Usuario;
import com.systemX.senai.enums.StatusProcesso;
import com.systemX.senai.enums.TipoUsuario;
import com.systemX.senai.exception.ResourceNotFoundException;
import com.systemX.senai.repository.HistoricoProcessoRepository;
import com.systemX.senai.repository.ProcessoContemplacaoRepository;
import com.systemX.senai.repository.UsuarioRepository;

@Service
public class ProcessoContemplacaoService {
    private final ProcessoContemplacaoRepository repository;
    private final HistoricoProcessoRepository historicoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoria;
    public ProcessoContemplacaoService(ProcessoContemplacaoRepository repository,
            HistoricoProcessoRepository historicoRepository, UsuarioRepository usuarioRepository,
            AuditoriaService auditoria) {
        this.repository = repository;
        this.historicoRepository = historicoRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoria = auditoria;
    }
    @Transactional(readOnly = true)
    public PaginaResponse<ProcessoResponse> listarPagina(int page, int size, String q, StatusProcesso status) {
        Specification<ProcessoContemplacao> filtro = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            if (status != null) predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            if (q != null && !q.isBlank()) {
                String termo = "%" + q.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                var cliente = root.join("cliente", JoinType.LEFT);
                var contemplacao = root.join("contemplacao", JoinType.LEFT);
                var cota = contemplacao.join("cota", JoinType.LEFT);
                var grupo = cota.join("grupo", JoinType.LEFT);
                Predicate busca = cb.or(
                        cb.like(cb.lower(cliente.get("nome")), termo, '\\'),
                        cb.like(cb.lower(cliente.get("email")), termo, '\\'),
                        cb.like(cb.lower(cota.get("numero")), termo, '\\'),
                        cb.like(cb.lower(grupo.get("numero")), termo, '\\'));
                try {
                    Long identificador = Long.valueOf(q.trim());
                    busca = cb.or(busca, cb.equal(root.get("id"), identificador),
                            cb.equal(contemplacao.get("id"), identificador));
                } catch (NumberFormatException ignored) {
                    // Keep textual matches when the query is not a numeric identifier.
                }
                predicate = cb.and(predicate, busca);
            }
            return predicate;
        };
        Page<ProcessoResponse> result = repository.findAll(filtro, Paginacao.pagina(page, size))
                .map(this::toResponse);
        return PaginaResponse.from(result);
    }
    @Transactional
    public ProcessoResponse atualizarStatus(Long id, StatusProcessoRequest request) {
        ProcessoContemplacao processo = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Processo não encontrado: " + id));
        Usuario usuario = usuarioRepository.findById(request.usuarioId()).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: " + request.usuarioId()));
        HistoricoProcesso historico = new HistoricoProcesso();
        historico.setProcesso(processo);
        historico.setStatusAnterior(processo.getStatus());
        historico.setNovoStatus(request.status());
        historico.setUsuario(usuario);
        historico.setData(LocalDateTime.now(ZoneOffset.UTC));
        historico.setObservacao(request.observacao());
        historicoRepository.save(historico);
        auditoria.registrar("Alteração de status", "Processo", id, processo.getStatus() + " → " + request.status());
        processo.setStatus(request.status()); processo.setResponsavel(usuario); processo.setAtualizadoEm(LocalDateTime.now(ZoneOffset.UTC));
        return toResponse(repository.save(processo));
    }
    @Transactional
    public ProcessoResponse atribuirResponsavel(Long id, AtribuirResponsavelRequest request) {
        ProcessoContemplacao processo = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Processo não encontrado: " + id));
        Usuario novoResponsavel = usuarioRepository.findById(request.responsavelId())
                .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado: " + request.responsavelId()));
        if (!novoResponsavel.isAtivo() || novoResponsavel.getTipo() != TipoUsuario.FUNCIONARIO) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Selecione um funcionário ativo para atribuir o processo.");
        }
        Usuario ator = auditoria.responsavel();
        if (ator == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Não foi possível identificar quem está atribuindo o processo.");
        }
        Usuario responsavelAnterior = processo.getResponsavel();
        if (responsavelAnterior != null && responsavelAnterior.getId().equals(novoResponsavel.getId())) {
            return toResponse(processo);
        }
        HistoricoProcesso historico = new HistoricoProcesso();
        historico.setProcesso(processo);
        historico.setStatusAnterior(processo.getStatus());
        historico.setNovoStatus(processo.getStatus());
        historico.setUsuario(ator);
        historico.setData(LocalDateTime.now(ZoneOffset.UTC));
        historico.setObservacao("Responsável alterado de " + nome(responsavelAnterior)
                + " para " + novoResponsavel.getNome() + ".");
        historicoRepository.save(historico);
        processo.setResponsavel(novoResponsavel);
        processo.setAtualizadoEm(LocalDateTime.now(ZoneOffset.UTC));
        auditoria.registrar("Atribuição de responsável", "Processo", id,
                nome(responsavelAnterior) + " → " + novoResponsavel.getNome());
        return toResponse(repository.save(processo));
    }
    @Transactional(readOnly = true)
    public ProcessoResponse buscar(Long id) { return toResponse(repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Processo não encontrado: " + id))); }
    private ProcessoResponse toResponse(ProcessoContemplacao p) {
        var contemplacao = p.getContemplacao();
        var cota = contemplacao.getCota();
        var grupo = cota.getGrupo();
        return new ProcessoResponse(p.getId(), contemplacao.getId(),
                p.getCliente() == null ? null : p.getCliente().getNome(),
                p.getCliente() == null ? null : p.getCliente().getEmail(),
                grupo == null ? null : grupo.getNumero(), cota.getNumero(), contemplacao.getValor(),
                p.getResponsavel() == null ? null : p.getResponsavel().getId(), p.getStatus(),
                p.getCriadoEm(), p.getAtualizadoEm());
    }
    private String nome(Usuario usuario) { return usuario == null ? "Sem responsável" : usuario.getNome(); }
}
