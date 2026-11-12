package com.systemX.senai.dto;
import com.systemX.senai.enums.StatusProcesso;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record ProcessoResponse(Long id, Long contemplacaoId, String clienteNome, String clienteEmail,
                               String grupoNumero, String cotaNumero, BigDecimal valorCredito,
                               Long responsavelId, StatusProcesso status,
                               LocalDateTime criadoEm, LocalDateTime atualizadoEm) {}
