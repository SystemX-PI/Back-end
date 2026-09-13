package com.systemX.senai.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systemX.senai.dto.ProcessoResponse;
import com.systemX.senai.dto.StatusProcessoRequest;
import com.systemX.senai.entity.HistoricoProcesso;
import com.systemX.senai.entity.ProcessoContemplacao;
import com.systemX.senai.entity.Usuario;
import com.systemX.senai.exception.ResourceNotFoundException;
import com.systemX.senai.repository.HistoricoProcessoRepository;
import com.systemX.senai.repository.ProcessoContemplacaoRepository;
import com.systemX.senai.repository.UsuarioRepository;

@Service
public class ProcessoContemplacaoService {
    private final ProcessoContemplacaoRepository repository;
    private final HistoricoProcessoRepository historicoRepository;
    private final UsuarioRepository usuarioRepository;
    public ProcessoContemplacaoService(ProcessoContemplacaoRepository repository,
            HistoricoProcessoRepository historicoRepository, UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.historicoRepository = historicoRepository;
        this.usuarioRepository = usuarioRepository;
    }
    @Transactional
    public ProcessoResponse atualizarStatus(Long id, StatusProcessoRequest request) {
        ProcessoContemplacao processo = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Processo não encontrado: " + id));
        Usuario usuario = usuarioRepository.findById(request.usuarioId()).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: " + request.usuarioId()));
        historicoRepository.save(HistoricoProcesso.builder().processo(processo).statusAnterior(processo.getStatus())
                .novoStatus(request.status()).usuario(usuario).data(LocalDateTime.now()).observacao(request.observacao()).build());
        processo.setStatus(request.status()); processo.setResponsavel(usuario); processo.setAtualizadoEm(LocalDateTime.now());
        return toResponse(repository.save(processo));
    }
    public ProcessoResponse buscar(Long id) { return toResponse(repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Processo não encontrado: " + id))); }
    private ProcessoResponse toResponse(ProcessoContemplacao p) { return new ProcessoResponse(p.getId(), p.getContemplacao().getId(), p.getResponsavel() == null ? null : p.getResponsavel().getId(), p.getStatus(), p.getCriadoEm(), p.getAtualizadoEm()); }
}