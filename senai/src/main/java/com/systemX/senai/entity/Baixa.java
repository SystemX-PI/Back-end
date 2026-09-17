package com.systemX.senai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Baixa {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) private ProcessoContemplacao processo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Usuario usuario;
    @Column(nullable = false) private LocalDateTime data;
    private String observacao;

    public Long getId() { return id; }
    public ProcessoContemplacao getProcesso() { return processo; }
    public void setProcesso(ProcessoContemplacao processo) { this.processo = processo; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public LocalDateTime getData() { return data; }
    public void setData(LocalDateTime data) { this.data = data; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
}