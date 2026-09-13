package com.systemX.senai.entity;

import com.systemX.senai.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HistoricoComunicacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Comunicacao comunicacao;
    @Column(nullable = false) private LocalDateTime data;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CanalComunicacao canal;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private StatusComunicacao status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Usuario usuario;
    private String resposta;
    private String observacao;
}