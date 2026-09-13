package com.systemX.senai.dto;
import com.systemX.senai.enums.StatusProcesso;
import java.time.LocalDateTime;
public record ProcessoResponse(Long id, Long contemplacaoId, Long responsavelId,
                               StatusProcesso status, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {}