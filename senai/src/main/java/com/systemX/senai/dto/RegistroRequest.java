package com.systemX.senai.dto;
import jakarta.validation.constraints.*;
public record RegistroRequest(@NotBlank @Size(max=255) String nome,
    @NotBlank @Email @Size(max=255) String email, @NotBlank @Size(min=6,max=72) String senha) {}
