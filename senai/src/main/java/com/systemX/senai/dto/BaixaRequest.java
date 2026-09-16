package com.systemX.senai.dto;

import jakarta.validation.constraints.NotNull;

public record BaixaRequest(@NotNull Long usuarioId, String observacao) {
}