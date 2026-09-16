package com.systemX.senai.dto;

import java.time.LocalDateTime;

public record BaixaResponse(Long id, Long processoId, Long usuarioId, LocalDateTime data, String observacao) {
}