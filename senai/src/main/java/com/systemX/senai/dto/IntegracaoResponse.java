package com.systemX.senai.dto;

public record IntegracaoResponse(
        Long processoId,
        Long comunicacaoId,
        String origem,
        String idExterno,
        String statusProcesso,
        String statusComunicacao,
        boolean jaExistia) {
}