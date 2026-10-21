package com.systemX.senai.dto;

public record EvolutionStatusResponse(
        String instanceName,
        String state,
        String code,
        String qrCode,
        String pairingCode) {}
