package br.com.livrostock.api;

import br.com.livrostock.domain.PerfilUsuario;
import br.com.livrostock.service.UsuarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("secure")
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {
    private final UsuarioService usuarios;

    public UsuarioController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    public record Cadastro(@NotBlank String nomeUsuario,
                           @NotBlank @Size(min = 12, max = 200) String senha,
                           @NotNull PerfilUsuario perfil) { }

    public record AlteracaoPerfil(@NotNull PerfilUsuario perfil) { }
    public record AlteracaoStatus(@NotNull Boolean ativo) { }
    public record AlteracaoSenha(@NotBlank @Size(min = 12, max = 200) String senha) { }

    @GetMapping
    public Page<UsuarioService.Resumo> listar(@PageableDefault(size = 20, sort = "nomeUsuario")
                                               Pageable pageable) {
        return usuarios.listar(pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioService.Resumo cadastrar(@Valid @RequestBody Cadastro request) {
        return usuarios.cadastrar(request.nomeUsuario(), request.senha(), request.perfil());
    }

    @PutMapping("/{id}/perfil")
    public UsuarioService.Resumo alterarPerfil(@PathVariable Long id,
                              @Valid @RequestBody AlteracaoPerfil request) {
        return usuarios.alterarPerfil(id, request.perfil());
    }

    @PatchMapping("/{id}/status")
    public UsuarioService.Resumo alterarStatus(@PathVariable Long id,
                              @Valid @RequestBody AlteracaoStatus request) {
        return usuarios.alterarStatus(id, request.ativo());
    }

    @PutMapping("/{id}/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void alterarSenha(@PathVariable Long id, @Valid @RequestBody AlteracaoSenha request) {
        usuarios.alterarSenha(id, request.senha());
    }
}
