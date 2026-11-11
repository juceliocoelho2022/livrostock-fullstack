package br.com.livrostock.service;

import br.com.livrostock.api.AcervoDigitalItemResponse;
import br.com.livrostock.api.IntegridadePdfResponse;
import br.com.livrostock.repository.ExemplarDigitalRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcervoDigitalService {
    private final ExemplarDigitalRepository exemplares;
    private final BibliotecaDigitalService biblioteca;

    public AcervoDigitalService(ExemplarDigitalRepository exemplares, BibliotecaDigitalService biblioteca) {
        this.exemplares = exemplares;
        this.biblioteca = biblioteca;
    }

    @Transactional(readOnly = true)
    public Page<AcervoDigitalItemResponse> listar(String busca, String categoria, int pagina, int tamanho) {
        return exemplares.buscarAcervoDigital(normalizar(busca, "busca", 120),
                normalizar(categoria, "categoria", 100), pagina(pagina, tamanho, 50))
                .map(AcervoDigitalItemResponse::from);
    }

    /**
     * Verificação sob demanda: apenas bibliotecários têm acesso ao endpoint.
     * Limite de 20 itens por chamada para evitar leituras excessivas de disco.
     */
    @Transactional(readOnly = true)
    public Page<IntegridadePdfResponse> verificarIntegridade(int pagina, int tamanho) {
        return exemplares.buscarAcervoDigital(null, null, pagina(pagina, tamanho, 20))
                .map(ex -> IntegridadePdfResponse.from(ex, biblioteca.verificarIntegridade(ex)));
    }

    private Pageable pagina(int numero, int tamanho, int limite) {
        if (numero < 0 || numero > 10000)
            throw new IllegalArgumentException("page deve estar entre 0 e 10000");
        if (tamanho < 1 || tamanho > limite)
            throw new IllegalArgumentException("size deve estar entre 1 e " + limite);
        return PageRequest.of(numero, tamanho,
                Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id")));
    }

    private String normalizar(String entrada, String campo, int maximo) {
        if (entrada == null || entrada.isBlank()) return null;
        String valor = entrada.strip();
        if (valor.length() > maximo)
            throw new IllegalArgumentException(campo + " deve ter no máximo " + maximo + " caracteres");
        return valor;
    }
}
