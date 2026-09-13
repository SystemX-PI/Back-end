package com.systemX.senai.entity;

import com.systemX.senai.enums.TipoLance;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Lance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Cota cota;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Assembleia assembleia;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TipoLance tipo;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal valor;
}