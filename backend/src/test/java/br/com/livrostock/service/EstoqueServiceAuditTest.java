package br.com.livrostock.service;

import br.com.livrostock.api.LivroRequest;
import br.com.livrostock.domain.Livro;
import br.com.livrostock.repository.LivroRepository;
import br.com.livrostock.repository.MovimentacaoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EstoqueServiceAuditTest {
    private LivroRepository livros;
    private MovimentacaoRepository movimentacoes;
    private AuditoriaService auditoria;
    private EstoqueService service;

    @BeforeEach
    void preparar() {
        livros = mock(LivroRepository.class);
        movimentacoes = mock(MovimentacaoRepository.class);
        auditoria = mock(AuditoriaService.class);
        service = new EstoqueService(livros, movimentacoes, auditoria);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "bibliotecario", "nunca-registrar", List.of(new SimpleGrantedAuthority("ROLE_BIBLIOTECARIO"))));
    }

    @AfterEach
    void limpar() { SecurityContextHolder.clearContext(); }

    @Test
    void criaLivroComRegistroDeAuditoria() {
        when(livros.save(any(Livro.class))).thenAnswer(i -> i.getArgument(0));
        LivroRequest request = new LivroRequest("Livro Teste", "Autoria Teste", "9780000000001",
                "Literatura", new BigDecimal("15.90"), 2);
        service.criar(request);
        verify(auditoria).registrar("bibliotecario", "LIVRO_CRIAR", "POST", "/api/v1/livros", 201);
    }

    @Test
    void desativarLivroFazAuditoriaObrigatoria() {
        Livro livro = new Livro("Teste", "Autor", "9780000000001",
                "Literatura", new BigDecimal("12.00"), 2);
        when(livros.bloquearPorId(7L)).thenReturn(Optional.of(livro));
        doThrow(new IllegalStateException("Auditoria indisponível"))
                .when(auditoria).registrar(any(), any(), any(), any(), anyInt());
        assertThrows(IllegalStateException.class, () -> service.desativar(7L));
        // Em produção, o @Transactional reverte livro e evento juntos.
        verify(auditoria).registrar("bibliotecario", "LIVRO_DESATIVAR", "DELETE",
                "/api/v1/livros/7", 204);
    }
}
