package br.com.livrostock.service;

import br.com.livrostock.api.AcervoDigitalItemResponse;
import br.com.livrostock.repository.ExemplarDigitalRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcervoDigitalService {
    private final ExemplarDigitalRepository exemplares;
    public AcervoDigitalService(ExemplarDigitalRepository exemplares) {
        this.exemplares = exemplares;
    }
    @Transactional(readOnly = true)
    public Page<AcervoDigitalItemResponse> listar(String busca, String categoria, int pagina, int tamanho) {
        if (pagina < 0 || pagina > 10000)
            throw new IllegalArgumentException("page deve estar entre 0 e 10000");
        if (tamanho < 1 || tamanho > 50)
            throw new IllegalArgumentException("size deve estar entre 1 e 50");
        Pageable pageable = PageRequest.of(pagina, tamanho,
                Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id")));
        return exemplares.buscarAcervoDigital(normalizar(busca,"busca",120),
                normalizar(categoria,"categoria",100),pageable).map(AcervoDigitalItemResponse::from);
    }
    private String normalizar(String entrada, String campo, int maximo) {
        if (entrada == null || entrada.isBlank()) return null;
        String valor = entrada.strip();
        if (valor.length() > maximo)
            throw new IllegalArgumentException(campo + " deve ter no máximo " + maximo + " caracteres");
        return valor;
    }
}
