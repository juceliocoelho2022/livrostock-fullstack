package br.com.livrostock.api;
import br.com.livrostock.domain.*;
import java.time.Instant;
public record MovimentacaoResponse(Long id,Long livroId,TipoMovimentacao tipo,int quantidade,int saldoApos,String observacao,Instant criadoEm){
 public static MovimentacaoResponse from(Movimentacao m){return new MovimentacaoResponse(m.getId(),m.getLivro().getId(),m.getTipo(),m.getQuantidade(),m.getSaldoApos(),m.getObservacao(),m.getCriadoEm());}
}
