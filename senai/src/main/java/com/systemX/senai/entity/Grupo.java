package com.systemX.senai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Grupo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private String numero;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Consorcio consorcio;
    private Integer quantidadeCotas;
    private BigDecimal valorParcela;
}