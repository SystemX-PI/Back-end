package com.systemX.senai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AtribuirResponsavelRequest(@NotNull @Positive Long responsavelId) {}
