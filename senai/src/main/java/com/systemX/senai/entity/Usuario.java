package com.systemX.senai.entity;

import com.systemX.senai.enums.TipoUsuario;
import java.time.Instant;

import jakarta.persistence.*;

@Entity
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    private String telefone;

    @Lob @Column(columnDefinition = "LONGTEXT")
    private String foto;
    public String getFoto() { return foto; }
    public void setFoto(String foto) { this.foto = foto; }

    @Column(name = "auth_provider", nullable = false, length = 20)
    private String authProvider = "LOCAL";

    @Column(name = "google_subject", unique = true, length = 255)
    private String googleSubject;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public String getAuthProvider() { return authProvider; }
    public void setAuthProvider(String authProvider) { this.authProvider = authProvider; }
    public String getGoogleSubject() { return googleSubject; }
    public void setGoogleSubject(String googleSubject) { this.googleSubject = googleSubject; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (authProvider == null || authProvider.isBlank()) authProvider = "LOCAL";
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    private Long contaId;
    public Long getContaId() { return contaId; }
    public void setContaId(Long contaId) { this.contaId = contaId; }

    @Column(nullable = false)
    private String senha;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private TipoUsuario tipo;

    private boolean ativo = true;

    @Column(nullable = false)
    private int versaoAutenticacao = 0;

    public int getVersaoAutenticacao() { return versaoAutenticacao; }
    public void invalidarSessoes() { versaoAutenticacao++; }


    public Usuario() {
    }

    public Usuario(Long id, String nome, String email, String senha, TipoUsuario tipo, boolean ativo) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.senha = senha;
        this.tipo = tipo;
        this.ativo = ativo;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public TipoUsuario getTipo() {
        return tipo;
    }

    public void setTipo(TipoUsuario tipo) {
        this.tipo = tipo;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
