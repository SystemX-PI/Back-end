package com.systemX.senai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Cota {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String numero;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Grupo grupo;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal valorCredito;
    @Builder.Default private boolean ativa = true;
}