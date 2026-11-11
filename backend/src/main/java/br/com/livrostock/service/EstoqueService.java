package br.com.livrostock.service;

import br.com.livrostock.api.*;
import br.com.livrostock.domain.*;
import br.com.livrostock.repository.*;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstoqueService {
    private final LivroRepository livros;
    private final MovimentacaoRepository movimentacoes;
    private final AuditoriaService auditoria;

    public EstoqueService(LivroRepository livros, MovimentacaoRepository movimentacoes,
                          AuditoriaService auditoria) {
        this.livros = livros;
        this.movimentacoes = movimentacoes;
        this.auditoria = auditoria;
    }

    @Transactional
    public LivroResponse criar(LivroRequest r) {
        if (livros.existsByIsbn(r.isbn())) throw new IllegalStateException("ISBN já cadastrado");
        Livro livro = livros.save(new Livro(r.titulo(), r.autor(), r.isbn(), r.categoria(),
                r.preco(), r.estoqueMinimo()));
        registrar("LIVRO_CRIAR", "POST", "/api/v1/livros", 201);
        return LivroResponse.from(livro);
    }

    @Transactional(readOnly = true)
    public Page<LivroResponse> listar(String busca, Pageable pageable) {
        return livros.buscarAtivos(busca == null || busca.isBlank() ? null : busca.strip(), pageable)
                .map(LivroResponse::from);
    }

    @Transactional(readOnly = true)
    public LivroResponse consultar(Long id) {
        return LivroResponse.from(livros.findById(id).filter(Livro::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Livro não encontrado")));
    }

    @Transactional
    public LivroResponse atualizar(Long id, LivroRequest r) {
        Livro livro = livros.bloquearPorId(id).filter(Livro::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Livro não encontrado"));
        if (!livro.getIsbn().equals(r.isbn())) {
            throw new IllegalArgumentException("ISBN não pode ser alterado");
        }
        livro.atualizar(r.titulo(), r.autor(), r.categoria(), r.preco(), r.estoqueMinimo());
        registrar("LIVRO_EDITAR", "PUT", "/api/v1/livros/" + id, 200);
        return LivroResponse.from(livro);
    }

    @Transactional
    public void desativar(Long id) {
        Livro livro = livros.bloquearPorId(id).filter(Livro::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Livro não encontrado"));
        livro.desativar();
        registrar("LIVRO_DESATIVAR", "DELETE", "/api/v1/livros/" + id, 204);
    }

    @Transactional
    public MovimentacaoResponse movimentar(Long id, MovimentacaoRequest r) {
        Livro livro = livros.bloquearPorId(id).filter(Livro::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Livro não encontrado"));
        int delta = r.tipo() == TipoMovimentacao.ENTRADA ? r.quantidade() : -r.quantidade();
        livro.movimentar(delta);
        Movimentacao movimento = movimentacoes.save(
                new Movimentacao(livro, r.tipo(), r.quantidade(), livro.getQuantidade(), r.observacao()));
        registrar("ESTOQUE_MOVIMENTAR", "POST", "/api/v1/livros/" + id + "/movimentacoes", 201);
        return MovimentacaoResponse.from(movimento);
    }

    @Transactional(readOnly = true)
    public Page<MovimentacaoResponse> historico(Long id, Pageable pageable) {
        consultar(id);
        return movimentacoes.findByLivroIdOrderByCriadoEmDesc(id, pageable).map(MovimentacaoResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<LivroResponse> estoqueBaixo(Pageable pageable) {
        return livros.estoqueBaixo(pageable).map(LivroResponse::from);
    }

    private void registrar(String acao, String metodo, String rota, int status) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) return;
        // AuditoriaService usa propagação REQUIRED: alteração de negócio e trilha na mesma transação.
        auditoria.registrar(auth.getName(), acao, metodo, rota, status);
    }
}
