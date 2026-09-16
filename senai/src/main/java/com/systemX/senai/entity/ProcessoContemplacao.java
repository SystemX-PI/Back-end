package com.systemX.senai.entity;

import com.systemX.senai.enums.StatusProcesso;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessoContemplacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) private Contemplacao contemplacao;
    @ManyToOne(fetch = FetchType.LAZY) private Usuario responsavel;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private StatusProcesso status;
    @Column private String idExterno;
    private String origem;
    @Builder.Default private LocalDateTime criadoEm = LocalDateTime.now();
    private LocalDateTime atualizadoEm;

    public Long getId() { return id; }
    public Contemplacao getContemplacao() { return contemplacao; }
    public void setContemplacao(Contemplacao contemplacao) { this.contemplacao = contemplacao; }
    public Usuario getResponsavel() { return responsavel; }
    public void setResponsavel(Usuario responsavel) { this.responsavel = responsavel; }
    public StatusProcesso getStatus() { return status; }
    public void setStatus(StatusProcesso status) { this.status = status; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(LocalDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }
    public String getIdExterno() { return idExterno; }
    public void setIdExterno(String idExterno) { this.idExterno = idExterno; }
    public String getOrigem() { return origem; }
    public void setOrigem(String origem) { this.origem = origem; }
}