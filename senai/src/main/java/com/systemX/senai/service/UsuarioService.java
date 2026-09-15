package com.systemX.senai.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.systemX.senai.dto.UsuarioRequest;
import com.systemX.senai.dto.UsuarioResponse;
import com.systemX.senai.entity.Usuario;
import com.systemX.senai.exception.ResourceNotFoundException;
import com.systemX.senai.repository.UsuarioRepository;

@Service
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
    public List<UsuarioResponse> listar() { return repository.findAll().stream().map(this::toResponse).toList(); }
    public Usuario buscar(Long id) { return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado: " + id)); }
    private UsuarioResponse toResponse(Usuario user) { return new UsuarioResponse(user.getId(), user.getNome(), user.getEmail(), user.getTipo(), user.isAtivo()); }
}