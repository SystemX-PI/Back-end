package com.systemX.senai.dto;
import jakarta.validation.constraints.*;
import com.systemX.senai.enums.TipoUsuario;
public record UsuarioAtualizacaoRequest(@NotBlank @Size(max=255) String nome,
    @NotBlank @Email @Size(max=255) String email, @NotNull TipoUsuario tipo, @NotNull Boolean ativo) {}
