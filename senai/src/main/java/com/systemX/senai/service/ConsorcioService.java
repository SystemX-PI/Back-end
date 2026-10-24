package com.systemX.senai.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systemX.senai.dto.ConsorcioRequest;
import com.systemX.senai.dto.ConsorcioResponse;
import com.systemX.senai.entity.Consorcio;
import com.systemX.senai.entity.TipoConsorcio;
import com.systemX.senai.exception.ResourceNotFoundException;
import com.systemX.senai.repository.ConsorcioRepository;
import com.systemX.senai.repository.TipoConsorcioRepository;

@Service
@Transactional
public class ConsorcioService {
    private final ConsorcioRepository repository;
    private final TipoConsorcioRepository tipoRepository;
    public ConsorcioService(ConsorcioRepository repository, TipoConsorcioRepository tipoRepository) {
        this.repository = repository;
        this.tipoRepository = tipoRepository;
    }
    public ConsorcioResponse criar(ConsorcioRequest request) {
        TipoConsorcio tipo = tipoRepository.findById(request.tipoId()).orElseThrow(() -> new ResourceNotFoundException("Tipo de consórcio não encontrado: " + request.tipoId()));
        Consorcio consorcio = new Consorcio(request.nome(), tipo);
        return toResponse(repository.save(consorcio));
    }
    @Transactional(readOnly = true)
    public List<ConsorcioResponse> listar() { return repository.findAll(Paginacao.pagina(0, 100)).stream().map(this::toResponse).toList(); }
    @Transactional(readOnly = true)
    public ConsorcioResponse consultar(Long id) { return toResponse(buscar(id)); }
    public ConsorcioResponse atualizar(Long id, ConsorcioRequest request) {
        Consorcio item = buscar(id);
        item.setNome(request.nome());
        item.setTipo(tipoRepository.findById(request.tipoId()).orElseThrow(() -> new ResourceNotFoundException("Tipo de consorcio nao encontrado: " + request.tipoId())));
        return toResponse(repository.save(item));
    }
    public void excluir(Long id) { repository.delete(buscar(id)); repository.flush(); }
    private Consorcio buscar(Long id) { return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Consorcio nao encontrado: " + id)); }
    @Transactional(readOnly = true)
    public com.systemX.senai.dto.PaginaResponse<ConsorcioResponse> listarPagina(int page, int size, String q) {
        return com.systemX.senai.dto.PaginaResponse.from(repository.findAll(Paginacao.<Consorcio>busca(q, "nome"), Paginacao.pagina(page, size)).map(this::toResponse));
    }
    private ConsorcioResponse toResponse(Consorcio item) { return new ConsorcioResponse(item.getId(), item.getNome(), item.getTipo().getId(), item.getTipo().getNome(), item.isAtivo()); }
}