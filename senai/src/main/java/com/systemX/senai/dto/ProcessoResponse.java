package com.systemX.senai.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.systemX.senai.enums.StatusProcesso;

public record ProcessoResponse(Long id, Long contemplacaoId, Long responsavelId,
                              StatusProcesso status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
    @JsonProperty("transicoesPermitidas")
    public List<StatusProcesso> transicoesPermitidas() {
        return status.transicoesPermitidas();
    }
}
