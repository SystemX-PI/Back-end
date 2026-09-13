package com.systemX.senai.dto;
import java.math.BigDecimal;
public record CotaResponse(Long id, String numero, Long grupoId, String grupoNumero,
                           BigDecimal valorCredito, boolean ativa) {}