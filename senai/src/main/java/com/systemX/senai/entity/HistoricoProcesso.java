package com.systemX.senai.entity;

import com.systemX.senai.enums.StatusProcesso;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HistoricoProcesso {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private ProcessoContemplacao processo;
    @Enumerated(EnumType.STRING) private StatusProcesso statusAnterior;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private StatusProcesso novoStatus;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Usuario usuario;
    @Column(nullable = false) private LocalDateTime data;
    private String observacao;
}