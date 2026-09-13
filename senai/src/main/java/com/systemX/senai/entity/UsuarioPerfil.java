package com.systemX.senai.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UsuarioPerfil {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Usuario usuario;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Perfil perfil;
    @Builder.Default private LocalDateTime criadoEm = LocalDateTime.now();
}