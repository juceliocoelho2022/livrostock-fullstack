# Contrato REST

Base local: `http://localhost:8081`

## Livros

- `GET /api/v1/livros?page=0&size=20&sort=titulo,asc`
- `GET /api/v1/livros?busca=Clean`
- `GET /api/v1/livros/1`
- `POST /api/v1/livros` — campos `titulo`, `autor`, `isbn`, `categoria`, `preco`, `estoqueMinimo`
- `PUT /api/v1/livros/1` — mesmos campos do cadastro
- `DELETE /api/v1/livros/1` — desativação lógica
- `GET /api/v1/livros/estoque-baixo`

## Movimentações

- `POST /api/v1/livros/1/movimentacoes`:

```json
{"tipo":"SAIDA","quantidade":3,"observacao":"Venda"}
```

- `GET /api/v1/livros/1/movimentacoes` — histórico paginado

## HTTP

- `201`: cadastro/movimentação criada
- `200`: consulta/alteração bem-sucedida
- `204`: desativação
- `400`: entrada inválida ou estoque insuficiente (versão atual)

Veja detalhes e modelos completos em `/swagger-ui.html`.
