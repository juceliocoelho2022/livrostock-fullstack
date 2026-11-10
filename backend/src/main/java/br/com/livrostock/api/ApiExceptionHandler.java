package br.com.livrostock.api;
import br.com.livrostock.service.RecursoNaoEncontradoException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.time.Instant;
import java.util.*;
@RestControllerAdvice
public class ApiExceptionHandler {
 private ResponseEntity<Map<String,Object>> erro(HttpStatus status,String msg){return ResponseEntity.status(status).body(Map.of("timestamp",Instant.now().toString(),"status",status.value(),"erro",msg));}
 @ExceptionHandler(RecursoNaoEncontradoException.class) public ResponseEntity<Map<String,Object>> naoEncontrado(RecursoNaoEncontradoException e){return erro(HttpStatus.NOT_FOUND,e.getMessage());}
 @ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class}) public ResponseEntity<Map<String,Object>> invalido(Exception e){return erro(HttpStatus.BAD_REQUEST,e instanceof HttpMessageNotReadableException?"JSON inválido ou tipo de movimentação desconhecido":e.getMessage());}
 @ExceptionHandler({IllegalStateException.class,DataIntegrityViolationException.class}) public ResponseEntity<Map<String,Object>> conflito(Exception e){return erro(HttpStatus.CONFLICT,"Conflito: ISBN duplicado ou integridade de dados");}
 @ExceptionHandler(MaxUploadSizeExceededException.class) public ResponseEntity<Map<String,Object>> arquivoGrande(MaxUploadSizeExceededException e){return erro(HttpStatus.PAYLOAD_TOO_LARGE,"O PDF excede o limite de 20 MiB");}
 @ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<Map<String,Object>> validacao(MethodArgumentNotValidException e){String msg=e.getBindingResult().getFieldErrors().stream().map(f->f.getField()+": "+f.getDefaultMessage()).distinct().reduce((a,b)->a+"; "+b).orElse("Dados inválidos");return erro(HttpStatus.BAD_REQUEST,msg);}
}
