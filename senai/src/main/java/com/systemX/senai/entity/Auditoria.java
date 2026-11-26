package com.systemX.senai.entity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Auditoria {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) private Usuario usuario;
    @Column(nullable = false) private String acao;
    @Column(nullable = false) private String entidade;
    private Long registroId;
    private Long contaId;
    @Column(nullable = false) @Builder.Default private LocalDateTime data = LocalDateTime.now(ZoneOffset.UTC);
    @Column(length = 2000) private String detalhes;
}
