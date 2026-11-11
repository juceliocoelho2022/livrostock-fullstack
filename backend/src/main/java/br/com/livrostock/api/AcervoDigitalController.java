package br.com.livrostock.api;

import br.com.livrostock.service.AcervoDigitalService;
import org.springframework.context.annotation.Profile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** A listagem devolve metadados; nunca expõe bytes PDF ou nomes de arquivos internos. */
@RestController
@Profile("secure")
@RequestMapping("/api/v1/acervo-digital")
@Tag(name = "Acervo digital", description = "Pesquisa paginada de livros digitais privados")
public class AcervoDigitalController {
    private final AcervoDigitalService acervo;
    public AcervoDigitalController(AcervoDigitalService acervo) { this.acervo = acervo; }
    @GetMapping
    @Operation(summary = "Pesquisar PDFs do acervo",
      description = "Busca por título ou autor, filtra por categoria e lista livros digitais ativos. Exige JWT no perfil secure.")
    public ResponseEntity<Page<AcervoDigitalItemResponse>> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) String categoria,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(acervo.listar(busca,categoria,page,size));
    }
}
