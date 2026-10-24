package com.systemX.senai.service;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.systemX.senai.dto.*;
import com.systemX.senai.entity.Grupo;
import com.systemX.senai.repository.*;
import com.systemX.senai.exception.ResourceNotFoundException;
@Service
@Transactional
public class GrupoService {
    private final GrupoRepository repository;
    private final ConsorcioRepository consorcioRepository;
    public GrupoService(GrupoRepository repository, ConsorcioRepository consorcioRepository) { this.repository = repository; this.consorcioRepository = consorcioRepository; }
    public GrupoResponse criar(GrupoRequest request) { return salvar(new Grupo(), request); }
    @Transactional(readOnly = true)
    public List<GrupoResponse> listar() { return repository.findAll(Paginacao.pagina(0, 100)).stream().map(this::toResponse).toList(); }
    @Transactional(readOnly = true)
    public GrupoResponse consultar(Long id) { return toResponse(buscar(id)); }
    public GrupoResponse atualizar(Long id, GrupoRequest request) { return salvar(buscar(id), request); }
    public void excluir(Long id) { repository.delete(buscar(id)); repository.flush(); }
    private Grupo buscar(Long id) { return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Grupo nao encontrado: " + id)); }
    private GrupoResponse salvar(Grupo item, GrupoRequest request) {
        item.setNumero(request.numero());
        item.setConsorcio(consorcioRepository.findById(request.consorcioId()).orElseThrow(() -> new ResourceNotFoundException("Consórcio nao encontrado: " + request.consorcioId())));
        item.setQuantidadeCotas(request.quantidadeCotas());
        item.setValorParcela(request.valorParcela());
        return toResponse(repository.save(item));
    }
    @Transactional(readOnly = true)
    public com.systemX.senai.dto.PaginaResponse<GrupoResponse> listarPagina(int page, int size, String q) {
        return com.systemX.senai.dto.PaginaResponse.from(repository.findAll(Paginacao.<Grupo>busca(q, "numero"), Paginacao.pagina(page, size)).map(this::toResponse));
    }
    private GrupoResponse toResponse(Grupo item) { return new GrupoResponse(item.getId(), item.getNumero(), item.getConsorcio().getId(), item.getQuantidadeCotas(), item.getValorParcela()); }
}
