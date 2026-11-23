package com.systemX.senai.dto;
import com.systemX.senai.enums.StatusProcesso;
import jakarta.validation.constraints.NotNull;
public record StatusProcessoRequest(@NotNull StatusProcesso status, String observacao) {}