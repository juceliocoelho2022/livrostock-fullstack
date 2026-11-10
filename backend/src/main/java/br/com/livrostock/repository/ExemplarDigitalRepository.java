package br.com.livrostock.repository;

import br.com.livrostock.domain.ExemplarDigital;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExemplarDigitalRepository extends JpaRepository<ExemplarDigital, Long> {
    List<ExemplarDigital> findByLivroIdOrderByCriadoEmDesc(Long livroId);
}
