package com.systemX.senai.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.systemX.senai.dto.ConsorcioRequest;
import com.systemX.senai.dto.ConsorcioResponse;
import com.systemX.senai.entity.Consorcio;
import com.systemX.senai.entity.TipoConsorcio;
import com.systemX.senai.exception.ResourceNotFoundException;
import com.systemX.senai.repository.ConsorcioRepository;
import com.systemX.senai.repository.TipoConsorcioRepository;

@Service
public class ConsorcioService {
    private final ConsorcioRepository repository;
    private final TipoConsorcioRepository tipoRepository;
    public ConsorcioService(ConsorcioRepository repository, TipoConsorcioRepository tipoRepository) {
        this.repository = repository;
        this.tipoRepository = tipoRepository;
    }
    public ConsorcioResponse criar(ConsorcioRequest request) {
        TipoConsorcio tipo = tipoRepository.findById(request.tipoId()).orElseThrow(() -> new ResourceNotFoundException("Tipo de consórcio não encontrado: " + request.tipoId()));
        Consorcio consorcio = new Consorcio();
        consorcio.setNome(request.nome());
        consorcio.setTipo(tipo);
        return toResponse(repository.save(consorcio));
    }
    public List<ConsorcioResponse> listar() { return repository.findAll().stream().map(this::toResponse).toList(); }
    private ConsorcioResponse toResponse(Consorcio item) { return new ConsorcioResponse(item.getId(), item.getNome(), item.getTipo().getId(), item.getTipo().getNome(), item.isAtivo()); }
}