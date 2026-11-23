package com.systemX.senai.dto;
import com.systemX.senai.enums.StatusProcesso;
import jakarta.validation.constraints.NotNull;
public record StatusProcessoRequest(@NotNull StatusProcesso status, @jakarta.validation.constraints.Size(max = 255) String observacao) {}