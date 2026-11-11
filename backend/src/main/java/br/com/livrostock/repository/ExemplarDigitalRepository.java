package br.com.livrostock.repository;

import br.com.livrostock.domain.ExemplarDigital;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ExemplarDigitalRepository extends JpaRepository<ExemplarDigital, Long> {
    List<ExemplarDigital> findByLivroIdOrderByCriadoEmDesc(Long livroId);

    // JOIN FETCH evita N+1 ao recuperar titulo/autor e countQuery preserva paginacao.
    @Query(value = """
        select e from ExemplarDigital e join fetch e.livro l
        where l.ativo = true and l.somenteDigital = true
          and (:busca is null
               or lower(l.titulo) like lower(concat('%', cast(:busca as string), '%'))
               or lower(l.autor) like lower(concat('%', cast(:busca as string), '%')))
          and (:categoria is null or lower(l.categoria) = lower(cast(:categoria as string)))
        """, countQuery = """
        select count(e) from ExemplarDigital e join e.livro l
        where l.ativo = true and l.somenteDigital = true
          and (:busca is null
               or lower(l.titulo) like lower(concat('%', cast(:busca as string), '%'))
               or lower(l.autor) like lower(concat('%', cast(:busca as string), '%')))
          and (:categoria is null or lower(l.categoria) = lower(cast(:categoria as string)))
        """)
    Page<ExemplarDigital> buscarAcervoDigital(@Param("busca") String busca,
                                               @Param("categoria") String categoria, Pageable pageable);
}
