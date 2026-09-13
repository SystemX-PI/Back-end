package com.systemX.senai.entity;

import com.systemX.senai.enums.StatusParcela;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Parcela {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Cota cota;
    @Column(nullable = false) private Integer numero;
    @Column(nullable = false) private LocalDate vencimento;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal valor;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private StatusParcela status;
}