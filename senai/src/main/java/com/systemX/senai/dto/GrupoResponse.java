package com.systemX.senai.dto;
import java.math.BigDecimal;
public record GrupoResponse(Long id, String numero, Long consorcioId, Integer quantidadeCotas, BigDecimal valorParcela) {}
