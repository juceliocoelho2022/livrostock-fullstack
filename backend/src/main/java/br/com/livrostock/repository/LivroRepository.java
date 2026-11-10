package br.com.livrostock.repository;
import br.com.livrostock.domain.Livro;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface LivroRepository extends JpaRepository<Livro,Long> {
 boolean existsByIsbn(String isbn);
 @Query("select l from Livro l where l.ativo = true and (:busca is null or lower(l.titulo) like lower(concat('%', cast(:busca as string), '%')) or lower(l.autor) like lower(concat('%', cast(:busca as string), '%')) or l.isbn = :busca)")
 Page<Livro> buscarAtivos(@Param("busca") String busca,Pageable pageable);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select l from Livro l where l.id = :id") Optional<Livro> bloquearPorId(@Param("id") Long id);
 @Query("select l from Livro l where l.ativo = true and l.quantidade <= l.estoqueMinimo") Page<Livro> estoqueBaixo(Pageable pageable);
}
