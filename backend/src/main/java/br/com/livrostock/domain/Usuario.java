package br.com.livrostock.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "usuarios")
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome_usuario", nullable = false, unique = true, length = 80)
    private String nomeUsuario;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PerfilUsuario perfil;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "versao_token", nullable = false)
    private int versaoToken;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Usuario() { }

    public Usuario(String nomeUsuario, String senhaHash, PerfilUsuario perfil) {
        this.nomeUsuario = nomeUsuario;
        this.senhaHash = senhaHash;
        this.perfil = perfil;
        this.ativo = true;
        this.versaoToken = 0;
        this.criadoEm = Instant.now();
        this.atualizadoEm = criadoEm;
    }

    public Long getId() { return id; }
    public String getNomeUsuario() { return nomeUsuario; }
    public String getSenhaHash() { return senhaHash; }
    public PerfilUsuario getPerfil() { return perfil; }
    public boolean isAtivo() { return ativo; }
    public int getVersaoToken() { return versaoToken; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }

    public void alterarSenha(String hash) {
        this.senhaHash = hash;
        invalidarTokens();
    }

    public void alterarPerfil(PerfilUsuario perfil) {
        if (this.perfil != perfil) {
            this.perfil = perfil;
            invalidarTokens();
        }
    }

    public void alterarAtivo(boolean ativo) {
        if (this.ativo != ativo) {
            this.ativo = ativo;
            invalidarTokens();
        }
    }

    private void invalidarTokens() {
        this.versaoToken++;
        this.atualizadoEm = Instant.now();
    }
}
