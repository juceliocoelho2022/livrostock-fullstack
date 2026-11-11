package br.com.livrostock.repository;

import br.com.livrostock.domain.PerfilUsuario;
import br.com.livrostock.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByNomeUsuario(String nomeUsuario);
    boolean existsByNomeUsuario(String nomeUsuario);
    long countByPerfilAndAtivoTrue(PerfilUsuario perfil);
}
