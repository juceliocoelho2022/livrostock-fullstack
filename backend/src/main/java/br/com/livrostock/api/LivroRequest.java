package br.com.livrostock.api;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record LivroRequest(@NotBlank @Size(max=200) String titulo,@NotBlank @Size(max=160) String autor,@NotBlank @Pattern(regexp="[0-9Xx-]{10,20}") String isbn,@NotBlank @Size(max=100) String categoria,@NotNull @DecimalMin("0.00") BigDecimal preco,@NotNull @PositiveOrZero Integer estoqueMinimo) {}
