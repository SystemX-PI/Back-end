package com.systemX.senai.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.systemX.senai.enums.TipoContemplacao;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ContempladoRequest(
        @NotBlank String origem,
        @NotBlank String idExterno,
        @NotBlank String nome,
        @NotBlank @Email String email,
        String telefone,
        @NotNull Long cotaId,
        @NotNull Long assembleiaId,
        @NotNull LocalDate dataContemplacao,
        @NotNull TipoContemplacao tipo,
        BigDecimal valorCredito) {
}