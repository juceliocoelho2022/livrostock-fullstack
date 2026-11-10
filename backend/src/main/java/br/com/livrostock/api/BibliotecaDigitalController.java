package br.com.livrostock.api;

import br.com.livrostock.domain.ExemplarDigital;
import br.com.livrostock.service.BibliotecaDigitalService;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/v1/livros/{livroId}/exemplares-digitais")
public class BibliotecaDigitalController {
    private final BibliotecaDigitalService service;

    public BibliotecaDigitalController(BibliotecaDigitalService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ExemplarDigitalResponse enviar(@PathVariable Long livroId,
                                           @RequestParam("arquivo") MultipartFile arquivo) {
        return ExemplarDigitalResponse.from(service.adicionar(livroId, arquivo));
    }

    @GetMapping
    public List<ExemplarDigitalResponse> listar(@PathVariable Long livroId) {
        return service.listar(livroId).stream().map(ExemplarDigitalResponse::from).toList();
    }

    @GetMapping("/{exemplarId}/arquivo")
    public ResponseEntity<Resource> abrir(@PathVariable Long livroId,
                                          @PathVariable Long exemplarId,
                                          @RequestParam(defaultValue = "false") boolean download) {
        ExemplarDigital exemplar = service.localizar(livroId, exemplarId);
        Resource arquivo = service.abrirArquivo(exemplar);
        String disposicao = (download ? "attachment" : "inline") + "; filename=\"exemplar.pdf\"";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicao)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(arquivo);
    }
}
