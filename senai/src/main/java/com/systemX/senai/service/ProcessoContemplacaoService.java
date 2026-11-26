package com.systemX.senai.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.systemX.senai.dto.ProcessoResponse;
import com.systemX.senai.dto.StatusProcessoRequest;
import com.systemX.senai.entity.HistoricoProcesso;
import com.systemX.senai.entity.ProcessoContemplacao;
import com.systemX.senai.dto.PaginaResponse;
import com.systemX.senai.enums.StatusProcesso;
import org.springframework.data.jpa.domain.Specification;
import com.systemX.senai.exception.ResourceNotFoundException;
import com.systemX.senai.repository.HistoricoProcessoRepository;
import com.systemX.senai.repository.ProcessoContemplacaoRepository;

@Service
public class ProcessoContemplacaoService {
    private final ProcessoContemplacaoRepository repository;
    private final HistoricoProcessoRepository historicoRepository;
    private final AuditoriaService auditoria;
    private final OperacaoAcesso acesso;
    public ProcessoContemplacaoService(ProcessoContemplacaoRepository repository,
            HistoricoProcessoRepository historicoRepository,
            AuditoriaService auditoria, OperacaoAcesso acesso) {
        this.repository = repository;
        this.historicoRepository = historicoRepository;
        this.auditoria = auditoria;
        this.acesso = acesso;
    }
    @Transactional
    public ProcessoResponse atualizarStatus(Long id, StatusProcessoRequest request) {
        var usuario = acesso.operador();
        ProcessoContemplacao processo = repository.bloquearPorId(id).orElseThrow(() -> new ResourceNotFoundException("Processo não encontrado: " + id));
        acesso.conferirProcesso(usuario, processo);
        acesso.conferirEscrita(usuario);
        if (!processo.getStatus().transicoesPermitidas().contains(request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta mudança de status não está disponível para a etapa atual. Consulte o andamento do processo.");
        }
        if (request.status() == StatusProcesso.CLIENTE_CONTATADO
                && (request.observacao() == null || request.observacao().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Descreva a evidência de contato confirmado com o cliente.");
        }
        HistoricoProcesso historico = new HistoricoProcesso();
        historico.setProcesso(processo);
        historico.setStatusAnterior(processo.getStatus());
        historico.setNovoStatus(request.status());
        historico.setUsuario(usuario);
        historico.setData(LocalDateTime.now(ZoneOffset.UTC));
        historico.setObservacao(request.observacao());
        historicoRepository.save(historico);
        auditoria.registrar("Alteração de status", "Processo", id, processo.getStatus() + " → " + request.status(),
                OperacaoAcesso.contaDoProcesso(processo));
        processo.setStatus(request.status()); processo.setResponsavel(usuario); processo.setAtualizadoEm(LocalDateTime.now(ZoneOffset.UTC));
        return toResponse(repository.save(processo));
    }
    @Transactional(readOnly = true)
    public PaginaResponse<ProcessoResponse> pagina(int page, int size, String q, StatusProcesso status) {
        var pageable = Paginacao.pagina(page, size);
        String termo = q == null ? "" : q.trim();
        Specification<ProcessoContemplacao> busca = Paginacao.busca(termo, "idExterno", "origem");
        Long id = numeroDoProcesso(termo);
        Specification<ProcessoContemplacao> filtroBusca = (root, query, cb) -> {
            var filtro = busca.toPredicate(root, query, cb);
            if (id != null) {
                filtro = cb.or(filtro, cb.equal(root.get("id"), id),
                        cb.equal(root.get("contemplacao").get("id"), id));
            }
            return status == null ? filtro : cb.and(filtro, cb.equal(root.get("status"), status));
        };
        var resultado = repository.findAll(acesso.processosVisiveis().and(filtroBusca), pageable);
        return PaginaResponse.from(resultado.map(this::toResponse));
    }

    private static Long numeroDoProcesso(String termo) {
        try {
            return Long.valueOf(termo);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public ProcessoResponse buscar(Long id) {
        var processo = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Processo não encontrado: " + id));
        acesso.conferirProcesso(processo);
        return toResponse(processo);
    }
    private ProcessoResponse toResponse(ProcessoContemplacao p) { return new ProcessoResponse(p.getId(), p.getContemplacao().getId(), p.getResponsavel() == null ? null : p.getResponsavel().getId(), p.getStatus(), p.getCriadoEm(), p.getAtualizadoEm()); }
}
