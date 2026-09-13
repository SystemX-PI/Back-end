package com.systemX.senai.dto;
import jakarta.validation.constraints.*;
public record ConsorcioRequest(@NotBlank String nome, @NotNull Long tipoId) {}