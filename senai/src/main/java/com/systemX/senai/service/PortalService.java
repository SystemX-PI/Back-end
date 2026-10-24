package com.systemX.senai.service;
import com.systemX.senai.dto.*;
import com.systemX.senai.entity.ProcessoContemplacao;
import com.systemX.senai.exception.ResourceNotFoundException;
import com.systemX.senai.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PortalService {
    private final ProcessoContemplacaoRepository processos;
    private final DocumentoRepository documentos;
    private final HistoricoProcessoRepository historicos;
    public PortalService(ProcessoContemplacaoRepository processos, DocumentoRepository documentos,
            HistoricoProcessoRepository historicos) {
        this.processos = processos; this.documentos = documentos; this.historicos = historicos;
    }
    public PaginaResponse<ProcessoResponse> listar(String email, int page, int size) {
        return PaginaResponse.from(processos.findByClienteEmail(email, Paginacao.pagina(page, size)).map(this::resumo));
    }
    public PortalProcessoResponse consultar(Long id, String email) {
        var p = processos.findByIdAndClienteEmail(id, email)
            .orElseThrow(() -> new ResourceNotFoundException("Processo nao encontrado."));
        var docs = documentos.findByProcessoId(id, Paginacao.pagina(0, 100)).stream().map(d ->
            new PortalProcessoResponse.DocumentoItem(d.getId(), d.getTipo().name(), d.getStatus().name(), d.getNomeArquivo(), d.getObservacao())).toList();
        var historico = historicos.findByProcessoId(id, Paginacao.pagina(0, 100)).stream().map(h ->
            new PortalProcessoResponse.HistoricoItem(h.getId(), h.getStatusAnterior() == null ? null : h.getStatusAnterior().name(),
                h.getNovoStatus().name(), h.getData(), h.getObservacao())).toList();
        return new PortalProcessoResponse(resumo(p), p.getResponsavel() == null ? null : p.getResponsavel().getNome(),
            p.getContemplacao().getCota().getNumero(), p.getContemplacao().getValor(), docs, historico);
    }
    private ProcessoResponse resumo(ProcessoContemplacao p) {
        return new ProcessoResponse(p.getId(), p.getContemplacao().getId(), p.getResponsavel() == null ? null : p.getResponsavel().getId(),
            p.getStatus(), p.getCriadoEm(), p.getAtualizadoEm());
    }
}
