package br.com.livrostock.domain;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="movimentacoes")
public class Movimentacao {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="livro_id") private Livro livro;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private TipoMovimentacao tipo;
 @Column(nullable=false) private int quantidade;
 @Column(nullable=false) private int saldoApos;
 @Column(length=250) private String observacao;
 @Column(nullable=false,updatable=false) private Instant criadoEm;
 protected Movimentacao() {}
 public Movimentacao(Livro livro,TipoMovimentacao tipo,int quantidade,int saldoApos,String observacao){this.livro=livro;this.tipo=tipo;this.quantidade=quantidade;this.saldoApos=saldoApos;this.observacao=observacao;this.criadoEm=Instant.now();}
 public Long getId(){return id;} public Livro getLivro(){return livro;} public TipoMovimentacao getTipo(){return tipo;} public int getQuantidade(){return quantidade;} public int getSaldoApos(){return saldoApos;} public String getObservacao(){return observacao;} public Instant getCriadoEm(){return criadoEm;}
}
