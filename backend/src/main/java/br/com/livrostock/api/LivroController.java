package br.com.livrostock.api;
import br.com.livrostock.service.EstoqueService;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
@RestController @RequestMapping("/api/v1/livros")
public class LivroController {
 private final EstoqueService service;
 public LivroController(EstoqueService service){this.service=service;}
 @PostMapping public ResponseEntity<LivroResponse> criar(@Valid @RequestBody LivroRequest request){LivroResponse livro=service.criar(request);return ResponseEntity.created(URI.create("/api/v1/livros/"+livro.id())).body(livro);}
 @GetMapping public Page<LivroResponse> listar(@RequestParam(required=false) String busca,@PageableDefault(size=20,sort="titulo") Pageable pageable){return service.listar(busca,pageable);}
 @GetMapping("/{id}") public LivroResponse consultar(@PathVariable Long id){return service.consultar(id);}
 @PutMapping("/{id}") public LivroResponse atualizar(@PathVariable Long id,@Valid @RequestBody LivroRequest request){return service.atualizar(id,request);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void desativar(@PathVariable Long id){service.desativar(id);}
 @PostMapping("/{id}/movimentacoes") @ResponseStatus(HttpStatus.CREATED) public MovimentacaoResponse movimentar(@PathVariable Long id,@Valid @RequestBody MovimentacaoRequest request){return service.movimentar(id,request);}
 @GetMapping("/{id}/movimentacoes") public Page<MovimentacaoResponse> historico(@PathVariable Long id,@PageableDefault(size=20) Pageable pageable){return service.historico(id,pageable);}
 @GetMapping("/estoque-baixo") public Page<LivroResponse> estoqueBaixo(@PageableDefault(size=20,sort="titulo") Pageable pageable){return service.estoqueBaixo(pageable);}
}
