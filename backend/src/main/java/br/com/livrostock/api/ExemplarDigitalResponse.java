package br.com.livrostock.api;

import br.com.livrostock.domain.ExemplarDigital;
import java.time.Instant;

public record ExemplarDigitalResponse(
        Long id, Long livroId, String nomeOriginal,
        String tipoMime, long tamanhoBytes, String sha256, Instant criadoEm
) {
    public static ExemplarDigitalResponse from(ExemplarDigital e) {
        return new ExemplarDigitalResponse(e.getId(), e.getLivro().getId(),
                e.getNomeOriginal(), e.getTipoMime(), e.getTamanhoBytes(), e.getSha256(), e.getCriadoEm());
    }
}
