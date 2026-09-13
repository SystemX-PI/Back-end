package com.systemX.senai.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record CotaRequest(@NotBlank String numero, @NotNull Long grupoId,
                          @NotNull @Positive BigDecimal valorCredito) {}