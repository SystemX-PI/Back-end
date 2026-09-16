package com.systemX.senai.entity;

import com.systemX.senai.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Contemplacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Cota cota;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Assembleia assembleia;
    @OneToOne(fetch = FetchType.LAZY) private Lance lance;
    @Column(nullable = false) private LocalDate data;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TipoContemplacao tipo;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal valor;

    public Long getId() { return id; }

    public static Contemplacao criar(Cota cota, Assembleia assembleia, LocalDate data,
            TipoContemplacao tipo, BigDecimal valor) {
        Contemplacao contemplacao = new Contemplacao();
        contemplacao.cota = cota;
        contemplacao.assembleia = assembleia;
        contemplacao.data = data;
        contemplacao.tipo = tipo;
        contemplacao.valor = valor;
        return contemplacao;
    }
}