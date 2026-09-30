package com.systemX.senai.dto;

import java.time.LocalDateTime;

public record FilaComunicacaoResponse(
        Long processoId,
        String origem,
        String idExterno,
        String statusProcesso,
        LocalDateTime recebidaEm,
        ComunicacaoResponse comunicacao) {
}
