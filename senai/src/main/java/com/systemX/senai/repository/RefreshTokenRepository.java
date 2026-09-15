package com.systemX.senai.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.systemX.senai.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}