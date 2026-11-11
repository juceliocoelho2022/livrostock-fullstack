package br.com.livrostock.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "auditoria_eventos")
public class AuditoriaEvento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String usuario;

    @Column(nullable = false, length = 60)
    private String acao;

    @Column(nullable = false, length = 10)
    private String metodo;

    @Column(nullable = false, length = 255)
    private String rota;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected AuditoriaEvento() { }

    public AuditoriaEvento(String usuario, String acao, String metodo, String rota, int httpStatus) {
        this.usuario = usuario;
        this.acao = acao;
        this.metodo = metodo;
        this.rota = rota;
        this.httpStatus = httpStatus;
        this.criadoEm = Instant.now();
    }

    public Long getId() { return id; }
    public String getUsuario() { return usuario; }
    public String getAcao() { return acao; }
    public String getMetodo() { return metodo; }
    public String getRota() { return rota; }
    public int getHttpStatus() { return httpStatus; }
    public Instant getCriadoEm() { return criadoEm; }
}
