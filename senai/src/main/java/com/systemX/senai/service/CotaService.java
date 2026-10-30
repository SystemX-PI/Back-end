package com.systemX.senai.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systemX.senai.dto.CotaRequest;
import com.systemX.senai.dto.CotaResponse;
import com.systemX.senai.entity.Cota;
import com.systemX.senai.entity.Grupo;
import com.systemX.senai.exception.ResourceNotFoundException;
import com.systemX.senai.repository.CotaRepository;
import com.systemX.senai.repository.GrupoRepository;

@Service
@Transactional
public class CotaService {
    private final CotaRepository repository;
    private final GrupoRepository grupoRepository;
    public CotaService(CotaRepository repository, GrupoRepository grupoRepository) {
        this.repository = repository;
        this.grupoRepository = grupoRepository;
    }
    public CotaResponse criar(CotaRequest request) {
        Grupo grupo = grupoRepository.findById(request.grupoId()).orElseThrow(() -> new ResourceNotFoundException("Grupo não encontrado: " + request.grupoId()));
        Cota cota = new Cota(request.numero(), grupo, request.valorCredito());
        return toResponse(repository.save(cota));
    }
    @Transactional(readOnly = true)
    public List<CotaResponse> listar() { return repository.findAll(Paginacao.pagina(0, 100)).stream().map(this::toResponse).toList(); }
    @Transactional(readOnly = true)
    public CotaResponse consultar(Long id) { return toResponse(buscar(id)); }
    public CotaResponse atualizar(Long id, CotaRequest request) {
        Cota item = buscar(id);
        item.setNumero(request.numero());
        item.setGrupo(grupoRepository.findById(request.grupoId()).orElseThrow(() -> new ResourceNotFoundException("Grupo nao encontrado: " + request.grupoId())));
        item.setValorCredito(request.valorCredito());
        return toResponse(repository.save(item));
    }
    public void excluir(Long id) { repository.delete(buscar(id)); repository.flush(); }
    private Cota buscar(Long id) { return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Cota nao encontrado: " + id)); }
    @Transactional(readOnly = true)
    public com.systemX.senai.dto.PaginaResponse<CotaResponse> listarPagina(int page, int size, String q) {
        return com.systemX.senai.dto.PaginaResponse.from(repository.findAll(Paginacao.<Cota>busca(q, "numero"), Paginacao.pagina(page, size)).map(this::toResponse));
    }
    private CotaResponse toResponse(Cota item) { return new CotaResponse(item.getId(), item.getNumero(), item.getGrupo().getId(), item.getGrupo().getNumero(), item.getValorCredito(), item.isAtiva()); }
}
