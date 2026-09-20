package com.systemX.senai.dto;

import java.time.LocalDateTime;

public record CanalComunicacaoResponse(
        Long id,
        String destinatario,
        String status,
        String erro,
        LocalDateTime criadaEm,
        LocalDateTime enviadaEm) {
}