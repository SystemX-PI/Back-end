package com.systemX.senai.dto;

public record ComunicacaoResponse(
        Long processoId,
        String statusGeral,
        CanalComunicacaoResponse email,
        CanalComunicacaoResponse whatsapp) {
}
