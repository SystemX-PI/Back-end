package com.systemX.senai.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record TipoConsorcioRequest(@NotBlank String nome) {}
