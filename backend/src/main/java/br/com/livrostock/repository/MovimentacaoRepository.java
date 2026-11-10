package br.com.livrostock.repository;
import br.com.livrostock.domain.Movimentacao;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MovimentacaoRepository extends JpaRepository<Movimentacao,Long> {Page<Movimentacao> findByLivroIdOrderByCriadoEmDesc(Long livroId,Pageable pageable);}
