package com.systemX.senai.entity;

import jakarta.persistence.*;

@Entity
public class TipoConsorcio {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nome;

    protected TipoConsorcio() {
        // Required by JPA.
    }

    public TipoConsorcio(String nome) {
        this.nome = nome;
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
}
