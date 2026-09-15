package com.systemX.senai.dto;

public record AuthResponse(String token, String refreshToken, String tipo, long expiresIn) {
}