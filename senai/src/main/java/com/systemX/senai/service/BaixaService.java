package com.systemX.senai.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systemX.senai.dto.BaixaRequest;
import com.systemX.senai.dto.BaixaResponse;
import com.systemX.senai.entity.Baixa;
import com.systemX.senai.entity.ProcessoContemplacao;
import com.systemX.senai.enums.StatusProcesso;
import com.systemX.senai.exception.ResourceNotFoundException;
import com.systemX.senai.repository.BaixaRepository;
import com.systemX.senai.repository.ProcessoContemplacaoRepository;

@Service
public class BaixaService {
    private final BaixaRepository baixaRepository;
    private final ProcessoContemplacaoRepository processoRepository;
    private final AuditoriaService auditoria;

    public BaixaService(BaixaRepository baixaRepository, ProcessoContemplacaoRepository processoRepository,
            AuditoriaService auditoria) {
        this.baixaRepository = baixaRepository;
        this.processoRepository = processoRepository;
        this.auditoria = auditoria;
    }

    @Transactional
    public BaixaResponse criar(Long processoId, BaixaRequest request) {
        var usuario = auditoria.exigirOperador();
        ProcessoContemplacao processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new ResourceNotFoundException("Processo não encontrado: " + processoId));
        Baixa existente = baixaRepository.findByProcessoId(processoId).orElse(null);
        if (existente != null) return toResponse(existente);
        if (processo.getStatus() == StatusProcesso.CANCELADO) {
            throw new IllegalStateException("Processo cancelado não pode receber baixa");
        }
        Baixa baixa = new Baixa();
        baixa.setProcesso(processo);
        baixa.setUsuario(usuario);
        baixa.setData(LocalDateTime.now(ZoneOffset.UTC));
        baixa.setObservacao(request.observacao());
        processo.setStatus(StatusProcesso.FINALIZADO);
        processo.setResponsavel(usuario);
        processo.setAtualizadoEm(LocalDateTime.now(ZoneOffset.UTC));
        processoRepository.save(processo);
        auditoria.registrar("Baixa concluída", "Processo", processoId, "Processo finalizado");
        return toResponse(baixaRepository.save(baixa));
    }

    @Transactional(readOnly = true)
    public BaixaResponse consultar(Long processoId) {
        return baixaRepository.findByProcessoId(processoId).map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Baixa não encontrada para o processo: " + processoId));
    }

    private BaixaResponse toResponse(Baixa baixa) {
        return new BaixaResponse(baixa.getId(), baixa.getProcesso().getId(), baixa.getUsuario().getId(),
                baixa.getData(), baixa.getObservacao());
    }
}
