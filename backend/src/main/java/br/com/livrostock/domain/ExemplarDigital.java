package br.com.livrostock.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "exemplares_digitais")
public class ExemplarDigital {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "livro_id", nullable = false)
    private Livro livro;

    @Column(name = "nome_original", nullable = false, length = 255)
    private String nomeOriginal;

    @Column(name = "nome_armazenado", nullable = false, unique = true, length = 80)
    private String nomeArmazenado;

    @Column(name = "tipo_mime", nullable = false, length = 80)
    private String tipoMime;

    @Column(name = "tamanho_bytes", nullable = false)
    private long tamanhoBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected ExemplarDigital() { }

    public ExemplarDigital(Livro livro, String nomeOriginal, String nomeArmazenado, long tamanhoBytes, String sha256) {
        this.livro = livro;
        this.nomeOriginal = nomeOriginal;
        this.nomeArmazenado = nomeArmazenado;
        this.tipoMime = "application/pdf";
        this.tamanhoBytes = tamanhoBytes;
        this.sha256 = sha256;
        this.criadoEm = Instant.now();
    }

    public Long getId() { return id; }
    public Livro getLivro() { return livro; }
    public String getNomeOriginal() { return nomeOriginal; }
    public String getNomeArmazenado() { return nomeArmazenado; }
    public String getTipoMime() { return tipoMime; }
    public long getTamanhoBytes() { return tamanhoBytes; }
    public String getSha256() { return sha256; }
    public Instant getCriadoEm() { return criadoEm; }
}
