package com.systemX.senai.service;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.systemX.senai.dto.*;
import com.systemX.senai.entity.TipoConsorcio;
import com.systemX.senai.repository.*;
import com.systemX.senai.exception.ResourceNotFoundException;
@Service
@Transactional
public class TipoConsorcioService {
    private final TipoConsorcioRepository repository;

    public TipoConsorcioService(TipoConsorcioRepository repository) { this.repository = repository;  }
    public TipoConsorcioResponse criar(TipoConsorcioRequest request) { return toResponse(repository.save(new TipoConsorcio(request.nome()))); }
    @Transactional(readOnly = true)
    public List<TipoConsorcioResponse> listar() { return repository.findAll(Paginacao.pagina(0, 100)).stream().map(this::toResponse).toList(); }
    @Transactional(readOnly = true)
    public TipoConsorcioResponse consultar(Long id) { return toResponse(buscar(id)); }
    public TipoConsorcioResponse atualizar(Long id, TipoConsorcioRequest request) { return salvar(buscar(id), request); }
    public void excluir(Long id) { repository.delete(buscar(id)); repository.flush(); }
    private TipoConsorcio buscar(Long id) { return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("TipoConsorcio nao encontrado: " + id)); }
    private TipoConsorcioResponse salvar(TipoConsorcio item, TipoConsorcioRequest request) {
        item.setNome(request.nome());
        return toResponse(repository.save(item));
    }
    @Transactional(readOnly = true)
    public com.systemX.senai.dto.PaginaResponse<TipoConsorcioResponse> listarPagina(int page, int size, String q) {
        return com.systemX.senai.dto.PaginaResponse.from(repository.findAll(Paginacao.<TipoConsorcio>busca(q, "nome"), Paginacao.pagina(page, size)).map(this::toResponse));
    }
    private TipoConsorcioResponse toResponse(TipoConsorcio item) { return new TipoConsorcioResponse(item.getId(), item.getNome()); }
}
