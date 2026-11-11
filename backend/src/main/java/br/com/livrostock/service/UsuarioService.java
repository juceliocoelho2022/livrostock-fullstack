package br.com.livrostock.service;

import br.com.livrostock.domain.PerfilUsuario;
import br.com.livrostock.domain.Usuario;
import br.com.livrostock.repository.UsuarioRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
@Profile("secure")
public class UsuarioService implements UserDetailsService {
    private final UsuarioRepository repository;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder encoder,
                          AuditoriaService auditoria) {
        this.repository = repository;
        this.encoder = encoder;
        this.auditoria = auditoria;
    }

    public record Resumo(Long id, String nomeUsuario, PerfilUsuario perfil, boolean ativo,
                         Instant criadoEm, Instant atualizadoEm) {
        public static Resumo from(Usuario usuario) {
            return new Resumo(usuario.getId(), usuario.getNomeUsuario(), usuario.getPerfil(),
                    usuario.isAtivo(), usuario.getCriadoEm(), usuario.getAtualizadoEm());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = repository.findByNomeUsuario(normalizar(username))
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));
        return User.withUsername(usuario.getNomeUsuario())
                .password(usuario.getSenhaHash())
                .authorities("ROLE_" + usuario.getPerfil().name())
                .disabled(!usuario.isAtivo())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<Resumo> listar(Pageable pageable) {
        return repository.findAll(pageable).map(Resumo::from);
    }

    @Transactional
    public Resumo cadastrar(String nomeUsuario, String senha, PerfilUsuario perfil) {
        String nome = normalizar(nomeUsuario);
        validarNome(nome);
        validarSenha(senha);
        if (perfil == null) throw new IllegalArgumentException("Perfil obrigatório");
        if (repository.existsByNomeUsuario(nome)) throw new IllegalStateException("Usuário já cadastrado");

        Usuario usuario = repository.save(new Usuario(nome, encoder.encode(senha), perfil));
        registrar("USUARIO_CRIAR", "POST", "/api/v1/usuarios", 201);
        return Resumo.from(usuario);
    }

    @Transactional
    public Resumo alterarPerfil(Long id, PerfilUsuario novoPerfil) {
        if (novoPerfil == null) throw new IllegalArgumentException("Perfil obrigatório");
        Usuario usuario = buscar(id);
        if (usuario.isAtivo() && usuario.getPerfil() == PerfilUsuario.BIBLIOTECARIO
                && novoPerfil != PerfilUsuario.BIBLIOTECARIO) {
            protegerUltimoBibliotecario();
        }
        usuario.alterarPerfil(novoPerfil);
        registrar("USUARIO_PERFIL", "PUT", "/api/v1/usuarios/" + id + "/perfil", 200);
        return Resumo.from(usuario);
    }

    @Transactional
    public Resumo alterarStatus(Long id, boolean ativo) {
        Usuario usuario = buscar(id);
        if (usuario.isAtivo() && !ativo && usuario.getPerfil() == PerfilUsuario.BIBLIOTECARIO) {
            protegerUltimoBibliotecario();
        }
        usuario.alterarAtivo(ativo);
        registrar("USUARIO_STATUS", "PATCH", "/api/v1/usuarios/" + id + "/status", 200);
        return Resumo.from(usuario);
    }

    @Transactional
    public void alterarSenha(Long id, String novaSenha) {
        validarSenha(novaSenha);
        Usuario usuario = buscar(id);
        usuario.alterarSenha(encoder.encode(novaSenha));
        registrar("USUARIO_SENHA", "PUT", "/api/v1/usuarios/" + id + "/senha", 204);
    }

    /**
     * Migração segura da v0.4: criar dois usuários uma única vez, com as credenciais
     * fornecidas pelo ambiente. Reinícios posteriores nunca sobrescrevem senhas.
     */
    @Transactional
    public void inicializarSeVazio(String nomeBibliotecario, String senhaBibliotecario,
                                   String nomeLeitor, String senhaLeitor) {
        if (repository.count() > 0) return;
        String admin = normalizar(nomeBibliotecario);
        String reader = normalizar(nomeLeitor);
        validarNome(admin);
        validarNome(reader);
        validarSenha(senhaBibliotecario);
        validarSenha(senhaLeitor);
        if (admin.equals(reader)) throw new IllegalStateException("Usuários iniciais devem ser diferentes");
        repository.save(new Usuario(admin, encoder.encode(senhaBibliotecario),
                PerfilUsuario.BIBLIOTECARIO));
        repository.save(new Usuario(reader, encoder.encode(senhaLeitor), PerfilUsuario.LEITOR));
    }

    private Usuario buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }

    private void protegerUltimoBibliotecario() {
        if (repository.countByPerfilAndAtivoTrue(PerfilUsuario.BIBLIOTECARIO) <= 1) {
            throw new IllegalStateException("Não é permitido desativar ou rebaixar o último bibliotecário");
        }
    }

    private void registrar(String acao, String metodo, String rota, int httpStatus) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            // Participa da transação do usuário: falha de auditoria reverte a alteração.
            auditoria.registrar(auth.getName(), acao, metodo, rota, httpStatus);
        }
    }

    private static String normalizar(String nome) {
        return nome == null ? "" : nome.trim().toLowerCase(Locale.ROOT);
    }

    private static void validarNome(String nome) {
        if (!nome.matches("[a-z0-9._-]{3,80}")) {
            throw new IllegalArgumentException("Usuário deve ter 3 a 80 caracteres: letras, números, ponto, traço ou sublinhado");
        }
    }

    private static void validarSenha(String senha) {
        if (senha == null || senha.length() < 12 || senha.length() > 200) {
            throw new IllegalArgumentException("A senha deve ter entre 12 e 200 caracteres");
        }
    }
}
