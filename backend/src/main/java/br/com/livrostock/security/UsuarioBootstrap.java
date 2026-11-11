package br.com.livrostock.security;

import br.com.livrostock.service.UsuarioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("secure")
public class UsuarioBootstrap implements ApplicationRunner {
    private final UsuarioService usuarios;
    private final String nomeBibliotecario;
    private final String senhaBibliotecario;
    private final String nomeLeitor;
    private final String senhaLeitor;

    public UsuarioBootstrap(UsuarioService usuarios,
            @Value("${LIVROSTOCK_LIBRARIAN_USER:bibliotecario}") String nomeBibliotecario,
            @Value("${LIVROSTOCK_LIBRARIAN_PASSWORD:}") String senhaBibliotecario,
            @Value("${LIVROSTOCK_READER_USER:leitor}") String nomeLeitor,
            @Value("${LIVROSTOCK_READER_PASSWORD:}") String senhaLeitor) {
        this.usuarios = usuarios;
        this.nomeBibliotecario = nomeBibliotecario;
        this.senhaBibliotecario = senhaBibliotecario;
        this.nomeLeitor = nomeLeitor;
        this.senhaLeitor = senhaLeitor;
    }

    @Override
    public void run(ApplicationArguments args) {
        usuarios.inicializarSeVazio(nomeBibliotecario, senhaBibliotecario, nomeLeitor, senhaLeitor);
    }
}
