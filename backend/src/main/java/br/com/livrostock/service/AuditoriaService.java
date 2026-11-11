package br.com.livrostock.service;

import br.com.livrostock.domain.AuditoriaEvento;
import br.com.livrostock.repository.AuditoriaEventoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditoriaService {
    private final AuditoriaEventoRepository repository;

    public AuditoriaService(AuditoriaEventoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registrar(String usuario, String acao, String metodo, String rota, int status) {
        repository.save(new AuditoriaEvento(usuario, acao, metodo, rota, status));
    }

    @Transactional(readOnly = true)
    public Page<AuditoriaEvento> listar(Pageable pageable) {
        return repository.findAll(pageable);
    }
}
