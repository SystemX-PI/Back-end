package com.systemX.senai.dto;
import com.systemX.senai.enums.TipoUsuario;
public record UsuarioResponse(Long id, String nome, String email, TipoUsuario tipo, boolean ativo) {}