package com.systemX.senai.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record GrupoRequest(@NotBlank String numero, @NotNull Long consorcioId, @Positive Integer quantidadeCotas, @Positive BigDecimal valorParcela) {}
