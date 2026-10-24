package com.systemX.senai.dto;
import jakarta.validation.constraints.*;
public record SenhaRequest(@NotBlank @Size(min=6,max=72) String senha) {}
