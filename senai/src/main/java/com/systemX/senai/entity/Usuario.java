package com.systemX.senai.entity;

import com.systemX.senai.enums.TipoUsuario;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String nome;
    @Column(nullable = false, unique = true) private String email;
    @Column(nullable = false) private String senha;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TipoUsuario tipo;
    @Builder.Default private boolean ativo = true;
}