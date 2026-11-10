package br.com.livrostock.domain;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class LivroTest {
 private Livro livro(){return new Livro("Clean Code","Robert Martin","9780132350884","Tecnologia",new BigDecimal("99.90"),2);}
 @Test void deveAdicionarEstoque(){Livro l=livro();l.movimentar(5);assertEquals(5,l.getQuantidade());}
 @Test void deveRejeitarSaidaSemSaldo(){Livro l=livro();assertThrows(IllegalArgumentException.class,()->l.movimentar(-1));}
 @Test void deveDesativarSemExcluir(){Livro l=livro();l.desativar();assertFalse(l.isAtivo());}
}
