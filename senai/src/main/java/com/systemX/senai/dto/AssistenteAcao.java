package com.systemX.senai.dto;

public record AssistenteAcao(
        String token,
        String tipo,
        Long processoId,
        String telefone,
        String mensagem,
        String aviso) {
}
