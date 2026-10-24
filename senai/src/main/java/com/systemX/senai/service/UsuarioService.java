package com.systemX.senai.service;

import java.util.List;
import com.systemX.senai.dto.*;
import com.systemX.senai.enums.TipoUsuario;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.systemX.senai.dto.UsuarioRequest;
import com.systemX.senai.dto.UsuarioResponse;
import com.systemX.senai.entity.Usuario;
import com.systemX.senai.exception.ResourceNotFoundException;
import com.systemX.senai.repository.UsuarioRepository;

@Service
@Transactional
public class UsuarioService {
    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }
    public UsuarioResponse criar(UsuarioRequest request) {
        Usuario usuario = new Usuario();
        usuario.setNome(request.nome());
        usuario.setEmail(request.email());
        usuario.setSenha(passwordEncoder.encode(request.senha()));
        usuario.setTipo(request.tipo());
        return toResponse(repository.save(usuario));
    }
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() { return repository.findAll(Paginacao.pagina(0, 100)).stream().map(this::toResponse).toList(); }
    public Usuario buscar(Long id) { return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: " + id)); }
    @Transactional(readOnly = true)
    public UsuarioResponse consultar(Long id) { return toResponse(buscar(id)); }
    public UsuarioResponse registrar(RegistroRequest request) {
        return criar(new UsuarioRequest(request.nome(), request.email(), request.senha(), TipoUsuario.CLIENTE));
    }
    public UsuarioResponse atualizar(Long id, UsuarioAtualizacaoRequest request) {
        Usuario item = buscar(id);
        if (item.getTipo() == TipoUsuario.ADMINISTRADOR && item.isAtivo()
                && (request.tipo() != TipoUsuario.ADMINISTRADOR || !request.ativo())) protegerAdministrador(item);
        item.setNome(request.nome());
        if (!item.getEmail().equals(request.email()) || item.getTipo() != request.tipo() || item.isAtivo() != request.ativo()) {
            item.invalidarSessoes();
        }
        item.setEmail(request.email());
        item.setAtivo(request.ativo());
        item.setTipo(request.tipo());
        return toResponse(repository.save(item));
    }
    public void alterarSenha(Long id, SenhaRequest request) {
        Usuario item = buscar(id);
        item.setSenha(passwordEncoder.encode(request.senha()));
        item.invalidarSessoes();
        repository.save(item);
    }
    public void excluir(Long id) {
        Usuario item = buscar(id); protegerAdministrador(item);
        repository.delete(item); repository.flush();
    }
    private void protegerAdministrador(Usuario item) {
        if (item.getTipo() == TipoUsuario.ADMINISTRADOR && item.isAtivo()
                && repository.countByTipoAndAtivoTrue(TipoUsuario.ADMINISTRADOR) <= 1)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                "Mantenha pelo menos um administrador ativo.");
    }
    @Transactional(readOnly = true)
    public com.systemX.senai.dto.PaginaResponse<UsuarioResponse> listarPagina(int page, int size, String q, com.systemX.senai.enums.TipoUsuario tipo) {
        return com.systemX.senai.dto.PaginaResponse.from(repository.findAll(Paginacao.<Usuario>busca(q, "nome", "email") .and((root, query, cb) -> tipo == null ? cb.conjunction() : cb.equal(root.get("tipo"), tipo)), Paginacao.pagina(page, size)).map(this::toResponse));
    }
    private UsuarioResponse toResponse(Usuario user) { return new UsuarioResponse(user.getId(), user.getNome(), user.getEmail(), user.getTipo(), user.isAtivo()); }
}