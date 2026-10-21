package com.systemX.senai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EvolutionQrRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,60}", message = "Use de 1 a 60 letras, números, hífen ou sublinhado")
        String instanceName) {}
