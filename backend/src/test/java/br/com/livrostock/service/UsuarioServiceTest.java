package br.com.livrostock.service;

import br.com.livrostock.domain.PerfilUsuario;
import br.com.livrostock.domain.Usuario;
import br.com.livrostock.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UsuarioServiceTest {
    private UsuarioRepository repository;
    private AuditoriaService auditoria;
    private BCryptPasswordEncoder encoder;
    private UsuarioService service;

    @BeforeEach
    void preparar() {
        repository = mock(UsuarioRepository.class);
        auditoria = mock(AuditoriaService.class);
        encoder = new BCryptPasswordEncoder();
        service = new UsuarioService(repository, encoder, auditoria);
    }

    @Test void senhaInicialEArmazenadaComoHash() {
        when(repository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        UsuarioService.Resumo resumo = service.cadastrar(
                "Maria.Silva", "senha-grande-123", PerfilUsuario.LEITOR);
        assertEquals("maria.silva", resumo.nomeUsuario());
        var captor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(repository).save(captor.capture());
        assertNotEquals("senha-grande-123", captor.getValue().getSenhaHash());
        assertTrue(encoder.matches("senha-grande-123", captor.getValue().getSenhaHash()));
    }

    @Test void usuarioDesativadoNaoPodeAutenticar() {
        Usuario usuario = new Usuario("leitor", encoder.encode("senha-laboratorio-123"), PerfilUsuario.LEITOR);
        usuario.alterarAtivo(false);
        when(repository.findByNomeUsuario("leitor")).thenReturn(Optional.of(usuario));
        assertFalse(service.loadUserByUsername("leitor").isEnabled());
    }

    @Test void naoPermiteDesativarUltimoBibliotecario() {
        Usuario usuario = new Usuario("bibliotecario", encoder.encode("senha-laboratorio-123"),
                PerfilUsuario.BIBLIOTECARIO);
        when(repository.findById(7L)).thenReturn(Optional.of(usuario));
        when(repository.countByPerfilAndAtivoTrue(PerfilUsuario.BIBLIOTECARIO)).thenReturn(1L);
        assertThrows(IllegalStateException.class, () -> service.alterarStatus(7L, false));
        assertTrue(usuario.isAtivo());
        verifyNoInteractions(auditoria);
    }

    @Test void naoPermiteRebaixarUltimoBibliotecario() {
        Usuario usuario = new Usuario("bibliotecario", encoder.encode("senha-laboratorio-123"),
                PerfilUsuario.BIBLIOTECARIO);
        when(repository.findById(7L)).thenReturn(Optional.of(usuario));
        when(repository.countByPerfilAndAtivoTrue(PerfilUsuario.BIBLIOTECARIO)).thenReturn(1L);
        assertThrows(IllegalStateException.class,
                () -> service.alterarPerfil(7L, PerfilUsuario.LEITOR));
        assertEquals(PerfilUsuario.BIBLIOTECARIO, usuario.getPerfil());
    }

    @Test void alterarSenhaIncrementaVersaoDoTokenSemVazarHash() {
        Usuario usuario = new Usuario("leitor", encoder.encode("senha-laboratorio-123"), PerfilUsuario.LEITOR);
        when(repository.findById(1L)).thenReturn(Optional.of(usuario));
        service.alterarSenha(1L, "outra-senha-mais-forte-123");
        assertEquals(1, usuario.getVersaoToken());
        assertTrue(encoder.matches("outra-senha-mais-forte-123", usuario.getSenhaHash()));
    }

    @Test void bootstrapReutilizaContasEJamaisSobrescreveSenhas() {
        when(repository.count()).thenReturn(2L);
        service.inicializarSeVazio("bibliotecario", "", "leitor", "");
        verify(repository, never()).save(any());
    }

    @Test void bootstrapCriaSomenteContasIniciaisComHash() {
        when(repository.count()).thenReturn(0L);
        service.inicializarSeVazio("bibliotecario", "senha-admin-segura-123",
                "leitor", "senha-leitor-segura-123");
        var captor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(repository, times(2)).save(captor.capture());
        assertEquals(PerfilUsuario.BIBLIOTECARIO, captor.getAllValues().get(0).getPerfil());
        assertEquals(PerfilUsuario.LEITOR, captor.getAllValues().get(1).getPerfil());
        assertTrue(encoder.matches("senha-admin-segura-123", captor.getAllValues().get(0).getSenhaHash()));
    }
}
