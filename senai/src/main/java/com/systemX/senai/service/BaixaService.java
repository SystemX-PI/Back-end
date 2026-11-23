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
import com.systemX.senai.entity.ProcessoContemplacao;
import com.systemX.senai.entity.Usuario;
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

    // A leitura do comprovante precisa enxergar a transação que acabou de liberar o bloqueio.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BaixaResponse criar(Long processoId, BaixaRequest request) {
        var usuario = auditoria.exigirOperador();
        ProcessoContemplacao processo = processoRepository.bloquearPorId(processoId)
                .orElseThrow(() -> new ResourceNotFoundException("Processo não encontrado: " + processoId));
        conferirConta(usuario, processo);
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
        processo.setStatus(StatusProcesso.FINALIZADO);
        processo.setResponsavel(usuario);
        processo.setAtualizadoEm(baixa.getData());
        processoRepository.save(processo);
        auditoria.registrar("Baixa concluída", "Processo", processoId, "Processo finalizado");
        return toResponse(baixaRepository.save(baixa));
    }

    @Transactional(readOnly = true)
    public BaixaResponse consultar(Long processoId) {
        var usuario = auditoria.exigirOperador();
        var processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new ResourceNotFoundException("Baixa não encontrada para o processo: " + processoId));
        conferirConta(usuario, processo);
        return baixaRepository.findByProcessoId(processoId).map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Baixa não encontrada para o processo: " + processoId));
    }

    private void conferirConta(Usuario operador, ProcessoContemplacao processo) {
        // Operadores da equipe interna sem conta vinculada mantêm o escopo operacional global.
        Long conta = operador.getContaId();
        if (conta != null && (processo.getCliente() == null || !conta.equals(processo.getCliente().getContaId()))) {
            throw new ResourceNotFoundException("Baixa não encontrada para o processo: " + processo.getId());
        }
    }

    private BaixaResponse toResponse(Baixa baixa) {
        return new BaixaResponse(baixa.getId(), baixa.getProcesso().getId(), baixa.getUsuario().getId(),
                baixa.getData(), baixa.getObservacao());
    }
}
