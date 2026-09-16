package com.systemX.senai.entity;

import com.systemX.senai.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Comunicacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private ProcessoContemplacao processo;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CanalComunicacao canal;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private StatusComunicacao status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Usuario responsavel;
    private String destinatario;
    private String assunto;
    @Column(length = 2000) private String mensagem;
    private LocalDateTime criadaEm;
    private LocalDateTime enviadaEm;
    private String erro;

    public Long getId() { return id; }
    public ProcessoContemplacao getProcesso() { return processo; }
    public void setProcesso(ProcessoContemplacao processo) { this.processo = processo; }
    public CanalComunicacao getCanal() { return canal; }
    public void setCanal(CanalComunicacao canal) { this.canal = canal; }
    public StatusComunicacao getStatus() { return status; }
    public void setStatus(StatusComunicacao status) { this.status = status; }
    public void setResponsavel(Usuario responsavel) { this.responsavel = responsavel; }
    public String getDestinatario() { return destinatario; }
    public void setDestinatario(String destinatario) { this.destinatario = destinatario; }
    public String getAssunto() { return assunto; }
    public void setAssunto(String assunto) { this.assunto = assunto; }
    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
    public void setCriadaEm(LocalDateTime criadaEm) { this.criadaEm = criadaEm; }
    public LocalDateTime getEnviadaEm() { return enviadaEm; }
    public void setEnviadaEm(LocalDateTime enviadaEm) { this.enviadaEm = enviadaEm; }
    public String getErro() { return erro; }
    public void setErro(String erro) { this.erro = erro; }
}