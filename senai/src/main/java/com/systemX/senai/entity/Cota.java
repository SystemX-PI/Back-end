package com.systemX.senai.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class Cota {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Grupo grupo;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valorCredito;

    private boolean ativa = true;

    public Cota() {
        // Required by JPA and used by the service layer when creating a new entity.
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

    public Grupo getGrupo() {
        return grupo;
    }

    public void setGrupo(Grupo grupo) {
        this.grupo = grupo;
    }

    public BigDecimal getValorCredito() {
        return valorCredito;
    }

    public void setValorCredito(BigDecimal valorCredito) {
        this.valorCredito = valorCredito;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public void setAtiva(boolean ativa) {
        this.ativa = ativa;
    }
}
