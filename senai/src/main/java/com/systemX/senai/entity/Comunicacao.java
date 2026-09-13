package com.systemX.senai.entity;

import com.systemX.senai.enums.*;
import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Comunicacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private ProcessoContemplacao processo;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CanalComunicacao canal;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private StatusComunicacao status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Usuario responsavel;
}