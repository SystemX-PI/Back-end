package com.systemX.senai.dto;

import java.time.LocalDateTime;

public record ComunicacaoResponse(
        Long processoId,
        String statusGeral,
        CanalComunicacaoResponse email,
        CanalComunicacaoResponse whatsapp) {
}