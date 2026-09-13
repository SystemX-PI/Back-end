package com.systemX.senai.entity;

import jakarta.persistence.*;

@Entity
public class Consorcio {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nome;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private TipoConsorcio tipo;

    private boolean ativo = true;

    public Consorcio() {
    }

    public Consorcio(String nome, TipoConsorcio tipo) {
        this.nome = nome;
        this.tipo = tipo;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public TipoConsorcio getTipo() {
        return tipo;
    }

    public void setTipo(TipoConsorcio tipo) {
        this.tipo = tipo;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}