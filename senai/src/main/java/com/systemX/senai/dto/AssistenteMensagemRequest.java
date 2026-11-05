package com.systemX.senai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssistenteMensagemRequest(
        @NotBlank @Size(max = 2000) String mensagem) {
}
