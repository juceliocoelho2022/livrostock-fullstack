package br.com.livrostock.api;

import br.com.livrostock.service.ImportacaoDigitalService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

/**
 * Importação local (arquivo previamente baixado do Drive).
 * Não recebe tokens do Google Drive e não cria compartilhamentos públicos.
 */
@RestController
@Profile("secure")
@RequestMapping("/api/v1/importacoes-digitais/drive")
public class ImportacaoDigitalController {
    private final ImportacaoDigitalService importacao;

    public ImportacaoDigitalController(ImportacaoDigitalService importacao) {
        this.importacao = importacao;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LivroResponse> importar(
            @RequestParam("driveFileId") String driveFileId,
            @RequestParam("titulo") String titulo,
            @RequestParam("autor") String autor,
            @RequestParam("categoria") String categoria,
            @RequestParam("arquivo") MultipartFile arquivo) {
        LivroResponse criado = importacao.importar(driveFileId, titulo, autor, categoria, arquivo);
        return ResponseEntity.created(URI.create("/api/v1/livros/" + criado.id())).body(criado);
    }
}
