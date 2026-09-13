package com.systemX.senai.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Consorcio {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String nome;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private TipoConsorcio tipo;
    @Builder.Default private boolean ativo = true;
}