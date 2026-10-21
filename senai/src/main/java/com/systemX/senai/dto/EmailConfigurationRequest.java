package com.systemX.senai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailConfigurationRequest(
        @NotBlank @Email String email,
        @NotBlank String senhaAplicativo) {}
