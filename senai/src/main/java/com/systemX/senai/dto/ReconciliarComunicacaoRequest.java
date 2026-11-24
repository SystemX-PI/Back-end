package com.systemX.senai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReconciliarComunicacaoRequest(@NotNull Resultado resultado, @NotBlank @Size(max = 255) String evidencia) {
    public enum Resultado { ACEITO_PROVEDOR, NAO_ENVIADO }
}
