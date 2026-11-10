package br.com.livrostock.api;
import br.com.livrostock.domain.TipoMovimentacao;
import jakarta.validation.constraints.*;
public record MovimentacaoRequest(@NotNull TipoMovimentacao tipo,@NotNull @Positive Integer quantidade,@Size(max=250) String observacao) {}
