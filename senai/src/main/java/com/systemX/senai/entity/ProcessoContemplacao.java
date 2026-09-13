package com.systemX.senai.entity;

import com.systemX.senai.enums.StatusProcesso;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessoContemplacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) private Contemplacao contemplacao;
    @ManyToOne(fetch = FetchType.LAZY) private Usuario responsavel;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private StatusProcesso status;
    @Builder.Default private LocalDateTime criadoEm = LocalDateTime.now();
    private LocalDateTime atualizadoEm;
}