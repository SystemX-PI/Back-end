package com.systemX.senai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Baixa {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) private ProcessoContemplacao processo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Usuario usuario;
    @Column(nullable = false) private LocalDateTime data;
    private String observacao;
}