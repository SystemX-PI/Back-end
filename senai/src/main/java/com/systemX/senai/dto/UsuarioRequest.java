package com.systemX.senai.dto;

import com.systemX.senai.enums.TipoUsuario;
import jakarta.validation.constraints.*;

public record UsuarioRequest(@NotBlank String nome, @NotBlank @Email String email,
                             @NotBlank @Size(min = 6) String senha,
                             @NotNull TipoUsuario tipo) {}