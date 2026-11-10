package br.com.livrostock.service;
import br.com.livrostock.api.*;
import br.com.livrostock.domain.*;
import br.com.livrostock.repository.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class EstoqueService {
 private final LivroRepository livros; private final MovimentacaoRepository movimentacoes;
 public EstoqueService(LivroRepository livros,MovimentacaoRepository movimentacoes){this.livros=livros;this.movimentacoes=movimentacoes;}
 @Transactional public LivroResponse criar(LivroRequest r){if(livros.existsByIsbn(r.isbn()))throw new IllegalStateException("ISBN já cadastrado");return LivroResponse.from(livros.save(new Livro(r.titulo(),r.autor(),r.isbn(),r.categoria(),r.preco(),r.estoqueMinimo())));}
 @Transactional(readOnly=true) public Page<LivroResponse> listar(String busca,Pageable pageable){return livros.buscarAtivos(busca==null || busca.isBlank()?null:busca.strip(),pageable).map(LivroResponse::from);}
 @Transactional(readOnly=true) public LivroResponse consultar(Long id){return LivroResponse.from(livros.findById(id).filter(Livro::isAtivo).orElseThrow(()->new RecursoNaoEncontradoException("Livro não encontrado")));}
 @Transactional public LivroResponse atualizar(Long id,LivroRequest r){Livro l=livros.bloquearPorId(id).filter(Livro::isAtivo).orElseThrow(()->new RecursoNaoEncontradoException("Livro não encontrado"));if(!l.getIsbn().equals(r.isbn()))throw new IllegalArgumentException("ISBN não pode ser alterado");l.atualizar(r.titulo(),r.autor(),r.categoria(),r.preco(),r.estoqueMinimo());return LivroResponse.from(l);}
 @Transactional public void desativar(Long id){Livro l=livros.bloquearPorId(id).filter(Livro::isAtivo).orElseThrow(()->new RecursoNaoEncontradoException("Livro não encontrado"));l.desativar();}
 @Transactional public MovimentacaoResponse movimentar(Long id,MovimentacaoRequest r){Livro l=livros.bloquearPorId(id).filter(Livro::isAtivo).orElseThrow(()->new RecursoNaoEncontradoException("Livro não encontrado"));int delta=r.tipo()==TipoMovimentacao.ENTRADA?r.quantidade():-r.quantidade();l.movimentar(delta);Movimentacao m=movimentacoes.save(new Movimentacao(l,r.tipo(),r.quantidade(),l.getQuantidade(),r.observacao()));return MovimentacaoResponse.from(m);}
 @Transactional(readOnly=true) public Page<MovimentacaoResponse> historico(Long id,Pageable pageable){consultar(id);return movimentacoes.findByLivroIdOrderByCriadoEmDesc(id,pageable).map(MovimentacaoResponse::from);}
 @Transactional(readOnly=true) public Page<LivroResponse> estoqueBaixo(Pageable pageable){return livros.estoqueBaixo(pageable).map(LivroResponse::from);}
}
