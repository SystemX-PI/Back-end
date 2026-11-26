package com.systemX.senai.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.systemX.senai.dto.BaixaRequest;
import com.systemX.senai.dto.BaixaResponse;
import com.systemX.senai.entity.Baixa;
import com.systemX.senai.entity.HistoricoProcesso;
import com.systemX.senai.entity.ProcessoContemplacao;
import com.systemX.senai.enums.StatusProcesso;
import com.systemX.senai.exception.ResourceNotFoundException;
import com.systemX.senai.repository.BaixaRepository;
import com.systemX.senai.repository.HistoricoProcessoRepository;
import com.systemX.senai.repository.ProcessoContemplacaoRepository;

@Service
public class BaixaService {
    private final BaixaRepository baixaRepository;
    private final ProcessoContemplacaoRepository processoRepository;
    private final HistoricoProcessoRepository historicoRepository;
    private final AuditoriaService auditoria;
    private final OperacaoAcesso acesso;

    public BaixaService(BaixaRepository baixaRepository, ProcessoContemplacaoRepository processoRepository,
            HistoricoProcessoRepository historicoRepository, AuditoriaService auditoria, OperacaoAcesso acesso) {
        this.baixaRepository = baixaRepository;
        this.processoRepository = processoRepository;
        this.historicoRepository = historicoRepository;
        this.auditoria = auditoria;
        this.acesso = acesso;
    }

    // A leitura do comprovante precisa enxergar a transação que acabou de liberar o bloqueio.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BaixaResponse criar(Long processoId, BaixaRequest request) {
        var usuario = acesso.operador();
        ProcessoContemplacao processo = processoRepository.bloquearPorId(processoId)
                .orElseThrow(() -> new ResourceNotFoundException("Processo não encontrado: " + processoId));
        acesso.conferirProcesso(usuario, processo);
        acesso.conferirEscrita(usuario);
        Baixa existente = baixaRepository.findByProcessoId(processoId).orElse(null);
        if (existente != null) return toResponse(existente);
        if (processo.getStatus() != StatusProcesso.EM_PROCESSO_DE_BAIXA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A baixa só pode ser concluída quando o processo está na etapa Em processo de baixa.");
        }
        Baixa baixa = new Baixa();
        baixa.setProcesso(processo);
        baixa.setUsuario(usuario);
        baixa.setData(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        baixa.setObservacao(request.observacao());
        var statusAnterior = processo.getStatus();
        processo.setStatus(StatusProcesso.FINALIZADO);
        processo.setResponsavel(usuario);
        processo.setAtualizadoEm(baixa.getData());
        processoRepository.save(processo);
        baixaRepository.save(baixa);
        historicoRepository.save(HistoricoProcesso.builder()
                .processo(processo)
                .statusAnterior(statusAnterior)
                .novoStatus(StatusProcesso.FINALIZADO)
                .usuario(usuario)
                .data(baixa.getData())
                .observacao(baixa.getObservacao())
                .build());
        auditoria.registrar("Baixa concluída", "Processo", processoId, "Processo finalizado",
                OperacaoAcesso.contaDoProcesso(processo));
        return toResponse(baixa);
    }

    @Transactional(readOnly = true)
    public BaixaResponse consultar(Long processoId) {
        var usuario = acesso.operador();
        var processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new ResourceNotFoundException("Processo não encontrado: " + processoId));
        acesso.conferirProcesso(usuario, processo);
        return baixaRepository.findByProcessoId(processoId).map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Baixa não encontrada para o processo: " + processoId));
    }

    private BaixaResponse toResponse(Baixa baixa) {
        return new BaixaResponse(baixa.getId(), baixa.getProcesso().getId(), baixa.getUsuario().getId(),
                baixa.getData(), baixa.getObservacao());
    }
}
