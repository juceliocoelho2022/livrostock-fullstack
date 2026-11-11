package br.com.livrostock.service;

import br.com.livrostock.api.LivroResponse;
import br.com.livrostock.domain.Livro;
import br.com.livrostock.repository.LivroRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportacaoDigitalService {
    private final LivroRepository livros;
    private final BibliotecaDigitalService biblioteca;
    private final AuditoriaService auditoria;

    public ImportacaoDigitalService(LivroRepository livros, BibliotecaDigitalService biblioteca,
                                    AuditoriaService auditoria) {
        this.livros = livros;
        this.biblioteca = biblioteca;
        this.auditoria = auditoria;
    }

    /**
     * A linha do catalogo, o exemplar PDF e a auditoria sao persistidos
     * dentro de uma unica transacao SQL; a biblioteca limpa o arquivo em rollback.
     */
    @Transactional
    public LivroResponse importar(String driveFileId, String titulo, String autor, String categoria,
                                  MultipartFile arquivo) {
        if (driveFileId == null || !driveFileId.matches("[a-zA-Z0-9_-]{10,128}")) {
            throw new IllegalArgumentException("Identificador do arquivo no Drive inválido");
        }
        titulo = validar(titulo, "Título", 200);
        autor = validar(autor, "Autor", 160);
        categoria = validar(categoria, "Categoria", 100);
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Informe um arquivo PDF não vazio");
        }
        if (livros.existsByOrigemDriveId(driveFileId)) {
            throw new IllegalStateException("Arquivo do Drive já importado. Nenhuma duplicação criada.");
        }
        Livro livro = livros.saveAndFlush(Livro.digital(titulo, autor, categoria, driveFileId));
        biblioteca.adicionar(livro.getId(), arquivo);
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.isAuthenticated()
                && !"anonymousUser".equals(autenticacao.getPrincipal())) {
            auditoria.registrar(autenticacao.getName(), "LIVRO_DIGITAL_IMPORTAR", "POST",
                    "/api/v1/importacoes-digitais/drive", 201);
        }
        return LivroResponse.from(livro);
    }

    private String validar(String valor, String campo, int maximo) {
        if (valor == null || valor.isBlank() || valor.strip().length() > maximo) {
            throw new IllegalArgumentException(campo + " deve conter entre 1 e " + maximo + " caracteres");
        }
        return valor.strip();
    }
}
