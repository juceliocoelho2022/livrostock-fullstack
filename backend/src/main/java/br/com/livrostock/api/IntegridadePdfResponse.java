package br.com.livrostock.api;

import br.com.livrostock.domain.ExemplarDigital;
import br.com.livrostock.service.BibliotecaDigitalService.EstadoIntegridade;

/** Auditoria de arquivos sem expor caminho local ou nome físico de armazenamento. */
public record IntegridadePdfResponse(
        Long livroId, Long exemplarId, String titulo,
        String nomeOriginal, long tamanhoEsperadoBytes, EstadoIntegridade estado) {

    public static IntegridadePdfResponse from(ExemplarDigital arquivo, EstadoIntegridade estado) {
        return new IntegridadePdfResponse(
                arquivo.getLivro().getId(), arquivo.getId(),
                arquivo.getLivro().getTitulo(), arquivo.getNomeOriginal(),
                arquivo.getTamanhoBytes(), estado);
    }
}
