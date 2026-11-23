package com.systemX.senai.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.systemX.senai.enums.TipoContemplacao;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ContempladoRequest(
        @NotBlank @Size(max = 255) String origem,
        @NotBlank @Size(max = 255) String idExterno,
        @NotBlank String nome,
        @NotBlank @Email String email,
        String telefone,
        @NotNull @Positive Long cotaId,
        @NotNull @Positive Long assembleiaId,
        @NotNull LocalDate dataContemplacao,
        @NotNull TipoContemplacao tipo,
        BigDecimal valorCredito) {
}