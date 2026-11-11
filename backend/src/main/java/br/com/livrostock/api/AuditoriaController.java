package br.com.livrostock.api;

import br.com.livrostock.domain.AuditoriaEvento;
import br.com.livrostock.service.AuditoriaService;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("secure")
@RequestMapping("/api/v1/auditoria")
public class AuditoriaController {
    private final AuditoriaService service;

    public AuditoriaController(AuditoriaService service) {
        this.service = service;
    }

    public record Registro(Long id, String usuario, String acao, String metodo,
                           String rota, int httpStatus, java.time.Instant criadoEm) {
        public static Registro from(AuditoriaEvento evento) {
            return new Registro(evento.getId(), evento.getUsuario(), evento.getAcao(),
                    evento.getMetodo(), evento.getRota(), evento.getHttpStatus(), evento.getCriadoEm());
        }
    }

    @GetMapping
    public Page<Registro> listar(@PageableDefault(size = 20, sort = "criadoEm",
            direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return service.listar(pageable).map(Registro::from);
    }
}
