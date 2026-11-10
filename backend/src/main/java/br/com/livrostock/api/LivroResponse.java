package br.com.livrostock.api;
import br.com.livrostock.domain.Livro;
import java.math.BigDecimal;
public record LivroResponse(Long id,String titulo,String autor,String isbn,String categoria,BigDecimal preco,int quantidade,int estoqueMinimo,boolean estoqueBaixo,boolean ativo){
 public static LivroResponse from(Livro l){return new LivroResponse(l.getId(),l.getTitulo(),l.getAutor(),l.getIsbn(),l.getCategoria(),l.getPreco(),l.getQuantidade(),l.getEstoqueMinimo(),l.getQuantidade()<=l.getEstoqueMinimo(),l.isAtivo());}
}
