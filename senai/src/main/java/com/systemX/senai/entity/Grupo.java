package com.systemX.senai.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class Grupo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Consorcio consorcio;

    private Integer quantidadeCotas;
    private BigDecimal valorParcela;

    protected Grupo() {
        // Required by JPA.
    }

    public Grupo(String numero, Consorcio consorcio, Integer quantidadeCotas, BigDecimal valorParcela) {
        this.numero = numero;
        this.consorcio = consorcio;
        this.quantidadeCotas = quantidadeCotas;
        this.valorParcela = valorParcela;
    }

    public Long getId() {
        return id;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public Consorcio getConsorcio() {
        return consorcio;
    }

    public void setConsorcio(Consorcio consorcio) {
        this.consorcio = consorcio;
    }

    public Integer getQuantidadeCotas() {
        return quantidadeCotas;
    }

    public void setQuantidadeCotas(Integer quantidadeCotas) {
        this.quantidadeCotas = quantidadeCotas;
    }

    public BigDecimal getValorParcela() {
        return valorParcela;
    }

    public void setValorParcela(BigDecimal valorParcela) {
        this.valorParcela = valorParcela;
    }
}
