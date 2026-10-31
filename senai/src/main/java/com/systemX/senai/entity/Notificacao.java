package com.systemX.senai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notificacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Usuario usuario;
    @ManyToOne(fetch = FetchType.LAZY) private ProcessoContemplacao processo;
    @Column(nullable = false) private String titulo;
    @Column(nullable = false, length = 1000) private String mensagem;
    @Builder.Default private boolean lida = false;
    @Builder.Default private LocalDateTime criadaEm = LocalDateTime.now(ZoneOffset.UTC);
}
