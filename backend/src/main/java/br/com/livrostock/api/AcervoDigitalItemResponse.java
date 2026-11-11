package br.com.livrostock.api;

import br.com.livrostock.domain.ExemplarDigital;
import br.com.livrostock.domain.Livro;
import java.time.Instant;

/** Metadados sem caminhos privados de armazenamento ou tokens. */
public record AcervoDigitalItemResponse(
        Long livroId, String titulo, String autor, String categoria,
        Long exemplarId, String nomeOriginal, String tipoMime,
        long tamanhoBytes, String sha256, Instant criadoEm) {
    public static AcervoDigitalItemResponse from(ExemplarDigital e) {
        Livro l = e.getLivro();
        return new AcervoDigitalItemResponse(l.getId(), l.getTitulo(), l.getAutor(),
                l.getCategoria(), e.getId(), e.getNomeOriginal(), e.getTipoMime(),
                e.getTamanhoBytes(), e.getSha256(), e.getCriadoEm());
    }
}
