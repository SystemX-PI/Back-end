package com.systemX.senai.dto;

import jakarta.validation.constraints.NotBlank;

public record WhatsAppRequest(
        @NotBlank String telefone,
        @NotBlank String mensagem) {
}