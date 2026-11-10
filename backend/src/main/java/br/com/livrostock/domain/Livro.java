package br.com.livrostock.domain;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity @Table(name="livros")
public class Livro {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=200) private String titulo;
 @Column(nullable=false,length=160) private String autor;
 @Column(nullable=false,unique=true,length=20) private String isbn;
 @Column(nullable=false,length=100) private String categoria;
 @Column(nullable=false,precision=12,scale=2) private BigDecimal preco;
 @Column(nullable=false) private int quantidade;
 @Column(nullable=false) private int estoqueMinimo;
 @Column(nullable=false) private boolean ativo=true;
 @Column(nullable=false,updatable=false) private Instant criadoEm;
 @PrePersist void onCreate(){ criadoEm=Instant.now(); }
 protected Livro() {}
 public Livro(String titulo,String autor,String isbn,String categoria,BigDecimal preco,int estoqueMinimo){this.titulo=titulo;this.autor=autor;this.isbn=isbn;this.categoria=categoria;this.preco=preco;this.estoqueMinimo=estoqueMinimo;}
 public void atualizar(String titulo,String autor,String categoria,BigDecimal preco,int estoqueMinimo){this.titulo=titulo;this.autor=autor;this.categoria=categoria;this.preco=preco;this.estoqueMinimo=estoqueMinimo;}
 public void movimentar(int delta){if((long)quantidade+delta<0 || (long)quantidade+delta>Integer.MAX_VALUE)throw new IllegalArgumentException("Quantidade resultante inválida");quantidade+=delta;}
 public void desativar(){ativo=false;}
 public Long getId(){return id;} public String getTitulo(){return titulo;} public String getAutor(){return autor;} public String getIsbn(){return isbn;} public String getCategoria(){return categoria;} public BigDecimal getPreco(){return preco;} public int getQuantidade(){return quantidade;} public int getEstoqueMinimo(){return estoqueMinimo;} public boolean isAtivo(){return ativo;} public Instant getCriadoEm(){return criadoEm;}
}
