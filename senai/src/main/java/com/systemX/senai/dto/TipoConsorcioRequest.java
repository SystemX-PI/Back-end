package com.systemX.senai.dto;
import jakarta.validation.constraints.*;
public record TipoConsorcioRequest(@NotBlank String nome) {}
