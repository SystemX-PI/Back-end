package com.systemX.senai.entity;

import com.systemX.senai.enums.*;
import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Documento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private ProcessoContemplacao processo;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TipoDocumento tipo;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private StatusDocumento status;
    private String nomeArquivo;
    private String observacao;
}