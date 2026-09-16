package com.systemX.senai.dto;

import java.time.LocalDateTime;

public record ComunicacaoResponse(
        Long id,
        Long processoId,
        String canal,
        String destinatario,
        String status,
        String erro,
        LocalDateTime criadaEm,
        LocalDateTime enviadaEm) {
}