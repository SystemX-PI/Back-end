package com.systemX.senai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record WhatsAppRequest(
        @NotBlank @Size(max = 40) String telefone,
        @NotBlank @Size(max = 4000) String mensagem,
        @Positive Long remetenteId) {
    public WhatsAppRequest(String telefone, String mensagem) {
        this(telefone, mensagem, null);
    }
}
