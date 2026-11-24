package com.systemX.senai.enums;

import java.util.List;

public enum StatusProcesso {
    CONTEMPLADO, AGUARDANDO_COMUNICACAO, EM_COMUNICACAO, CLIENTE_CONTATADO,
    AGUARDANDO_DOCUMENTOS, DOCUMENTOS_RECEBIDOS, EM_ANALISE,
    EM_PROCESSO_DE_BAIXA, FINALIZADO, CANCELADO;

    /** Mudanças manuais. Somente a baixa pode finalizar um processo. */
    public List<StatusProcesso> transicoesPermitidas() {
        return switch (this) {
            case CONTEMPLADO -> List.of(AGUARDANDO_COMUNICACAO, CANCELADO);
            case AGUARDANDO_COMUNICACAO -> List.of(EM_COMUNICACAO, CANCELADO);
            case EM_COMUNICACAO -> List.of(CLIENTE_CONTATADO, CANCELADO);
            case CLIENTE_CONTATADO -> List.of(AGUARDANDO_DOCUMENTOS, CANCELADO);
            case AGUARDANDO_DOCUMENTOS -> List.of(DOCUMENTOS_RECEBIDOS, CANCELADO);
            case DOCUMENTOS_RECEBIDOS -> List.of(EM_ANALISE, CANCELADO);
            case EM_ANALISE -> List.of(EM_PROCESSO_DE_BAIXA, CANCELADO);
            case EM_PROCESSO_DE_BAIXA -> List.of(CANCELADO);
            case FINALIZADO, CANCELADO -> List.of();
        };
    }
}
