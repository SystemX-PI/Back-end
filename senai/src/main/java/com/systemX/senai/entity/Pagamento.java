package com.systemX.senai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Pagamento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Parcela parcela;
    @Column(nullable = false) private LocalDate data;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal valor;
    private String comprovante;
}