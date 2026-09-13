package com.systemX.senai.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.systemX.senai.dto.CotaRequest;
import com.systemX.senai.dto.CotaResponse;
import com.systemX.senai.entity.Cota;
import com.systemX.senai.entity.Grupo;
import com.systemX.senai.exception.ResourceNotFoundException;
import com.systemX.senai.repository.CotaRepository;
import com.systemX.senai.repository.GrupoRepository;

@Service
public class CotaService {
    private final CotaRepository repository;
    private final GrupoRepository grupoRepository;
    public CotaService(CotaRepository repository, GrupoRepository grupoRepository) {
        this.repository = repository;
        this.grupoRepository = grupoRepository;
    }
    public CotaResponse criar(CotaRequest request) {
        Grupo grupo = grupoRepository.findById(request.grupoId()).orElseThrow(() -> new ResourceNotFoundException("Grupo não encontrado: " + request.grupoId()));
        return toResponse(repository.save(Cota.builder().numero(request.numero()).grupo(grupo).valorCredito(request.valorCredito()).build()));
    }
    public List<CotaResponse> listar() { return repository.findAll().stream().map(this::toResponse).toList(); }
    private CotaResponse toResponse(Cota item) { return new CotaResponse(item.getId(), item.getNumero(), item.getGrupo().getId(), item.getGrupo().getNumero(), item.getValorCredito(), item.isAtiva()); }
}