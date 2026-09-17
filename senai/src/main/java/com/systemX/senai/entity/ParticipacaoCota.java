package com.systemX.senai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ParticipacaoCota {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Usuario usuario;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Cota cota;
    @Column(nullable = false) private LocalDate inicio;
    private LocalDate fim;
    @Builder.Default private boolean ativa = true;

    public static ParticipacaoCota criar(Usuario usuario, Cota cota, LocalDate inicio) {
        ParticipacaoCota participacao = new ParticipacaoCota();
        participacao.usuario = usuario;
        participacao.cota = cota;
        participacao.inicio = inicio;
        participacao.ativa = true;
        return participacao;
    }
}