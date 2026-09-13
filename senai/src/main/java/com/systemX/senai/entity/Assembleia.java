package com.systemX.senai.entity;

import java.time.LocalDate;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Assembleia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Grupo grupo;
    @Column(nullable = false) private LocalDate data;
    private String observacao;
}