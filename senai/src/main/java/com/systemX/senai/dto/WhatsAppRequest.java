package com.systemX.senai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record WhatsAppRequest(
        @NotBlank String telefone,
        @NotBlank String mensagem,
        @Positive Long remetenteId) {
    public WhatsAppRequest(String telefone, String mensagem) {
        this(telefone, mensagem, null);
    }
}
