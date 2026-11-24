package com.systemX.senai.enums;

import java.util.Set;

public enum StatusComunicacao {
    PENDENTE,     // Registrada, ainda sem aceite.
    REALIZADA,    // Aceite legado sem comprovação de entrega.
    SUCESSO,      // Aceita pelo provedor sem comprovação de entrega.
    SEM_RESPOSTA, // Resultado não confirmado pelo provedor.
    FALHA;        // Tentativa não aceita ou falha local.

    public static final Set<StatusComunicacao> ACEITOS = Set.of(SUCESSO, REALIZADA);

    public boolean aceito() {
        return ACEITOS.contains(this);
    }
}
