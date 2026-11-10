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

## Biblioteca digital — backend inicial na branch v0.2 (não validado em ambiente local)

Os endpoints abaixo foram adicionados ao código e dependem da migration `V2__create_exemplares_digitais.sql`. Não há autenticação: **utilizar exclusivamente em desenvolvimento local com PDFs autorizados**.

### Enviar PDF

`POST /api/v1/livros/{livroId}/exemplares-digitais`

- `Content-Type: multipart/form-data`
- Campo obrigatório: `arquivo` (`.pdf`)
- Limite: **20 MiB**
- Validação inicial: extensão `.pdf`, arquivo não vazio e assinatura `%PDF-`
- Resposta prevista: `201 Created` com id, livroId, nomeOriginal, tipoMime, tamanhoBytes, sha256, criadoEm

PowerShell:

```powershell
# Ajuste 1 para o ID real do livro, e caminho para um PDF autorizado.
curl.exe -X POST "http://localhost:8081/api/v1/livros/1/exemplares-digitais" -F "arquivo=@C:\\Docs\\exemplar-autorizado.pdf;type=application/pdf"
```

### Listar PDFs associados

`GET /api/v1/livros/{livroId}/exemplares-digitais`

### Ler ou baixar arquivo

- `GET /api/v1/livros/{livroId}/exemplares-digitais/{exemplarId}/arquivo` — exibição inline no navegador
- `GET /api/v1/livros/{livroId}/exemplares-digitais/{exemplarId}/arquivo?download=true` — download

O retorno de leitura utiliza `application/pdf`, `Cache-Control: no-store` e `Content-Disposition` apropriado.

### Configuração de armazenamento

```powershell
$env:PDF_STORAGE_DIR = "C:\\Dados\\LivroStock\\pdfs"
```

A variável deve ser definida **antes** de iniciar o Spring Boot. O diretório precisa ser persistente e ter permissões de leitura/gravação para o processo Java. A configuração também inclui limites `spring.servlet.multipart.*`.

### Status e restrições

- Upload, listagem e leitura/download **codificados no backend**, aguardando compilação e teste de integração.
- Não há leitor PDF.js integrado ao React; navegadores modernos podem exibir diretamente o PDF retornado.
- Não existe login, proteção dos downloads, antivírus, controle de licença nem auditoria.
- A validação de cabeçalho não garante que o documento seja seguro.
- A consistência entre storage e banco, incluindo limpeza de arquivos órfãos em falhas transacionais, ainda precisa de robustecimento.
- Erros de limite no multipart podem resultar em `413` ou na resposta padrão do servidor; padronização com Problem Details fica no roadmap.
