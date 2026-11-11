# LivroStock v0.8 — API do Acervo Digital

Branch: `feature/v0.8-api-acervo-digital`. Continuação da v0.7, preservando acervo físico e os PDFs armazenados.

## Objetivo

Substituir a abordagem antiga de uma requisição por livro para listar arquivos digitais por uma **consulta paginada única** no backend Java 21/Spring Boot 3.5.6. No frontend React/TypeScript, incluir busca por título ou autor e filtro por categoria.

## Endpoint

`GET /api/v1/acervo-digital`

Parâmetros:

| Campo | Exemplo | Validação |
| --- | --- | --- |
| `busca` | `Spring` | Opcional; pesquisa por título ou autor; até 120 caracteres |
| `categoria` | `Tecnologia` | Opcional; comparação sem diferenciar maiúsculas/minúsculas; até 100 caracteres |
| `page` | `0` | 0 a 10000 |
| `size` | `10` | 1 a 50 (padrão: 20) |

Resposta: página Spring Data (`content`, `totalElements`, `totalPages`, `number`, `size`). Cada item contém `livroId`, `titulo`, `autor`, `categoria`, `exemplarId`, `nomeOriginal`, `tipoMime`, `tamanhoBytes`, `sha256`, `criadoEm`. Não expõe o nome interno nem caminho de armazenamento.

Somente exemplares vinculados a **livros digitais ativos** são retornados. Livros físicos com PDFs podem continuar sendo acessados via endpoint existente por livro, mas não entram neste índice especializado.

### Segurança

O endpoint existe **somente** no perfil `secure` (`@Profile("secure")`). Usuários com JWT válido e perfil `BIBLIOTECARIO` ou `LEITOR` acessam a lista; sem JWT retorna 401. No perfil `default`, o React mantém a listagem legada por livro, exclusiva para desenvolvimento local. Upload continua restrito a bibliotecário. `Cache-Control: no-store` impede que o endpoint solicite armazenamento da resposta em caches HTTP. PDFs permanecem privados e continuam sendo lidos pelo endpoint autenticado existente.

**Não publique a API no perfil padrão:** os módulos legados oferecem acesso local sem JWT, mesmo que este endpoint novo não seja criado nele. Para um deploy público, faltam revisão de segurança, rate limiting, HTTPS e política de licenças/uso de PDFs.

## Exemplo no PowerShell

Com a API em execução no perfil `secure`, faça login e consulte os metadados sem expor a senha no histórico:

```powershell
$usuario = Read-Host "Usuario LivroStock"
$senha = Read-Host "Senha LivroStock" -AsSecureString
$bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($senha)
try {
    $senhaTexto = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    $corpo = @{ usuario = $usuario; senha = $senhaTexto } | ConvertTo-Json
} finally {
    [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
}
$resposta = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $corpo
Remove-Variable corpo, senhaTexto -ErrorAction SilentlyContinue
$headers = @{ Authorization = "Bearer $($resposta.accessToken)" }
$dados = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/acervo-digital?size=10&busca=Spring" -Headers $headers
$dados.content | Select-Object titulo, autor, categoria, nomeOriginal
```

Não registre o JWT em logs ou capturas. O token expira automaticamente conforme a configuração do backend.

## Compatibilidade e testes

- Não adiciona migration Flyway nem altera o conteúdo dos PDFs.
- Mantém os endpoints da v0.7 para importar, ler e baixar PDFs.
- `AcervoDigitalApiIntegrationTest` cobre JWT, busca, categoria, paginação, arquivos privados, leitura e ocultação de livros inativos, com PostgreSQL descartável na CI.
- A mudança deve passar por `mvn test`, testes de integração em PostgreSQL isolado e `npm run build`.
- Antes de homologar no Windows, faça um backup novo de **PostgreSQL + diretório de PDFs** (v0.6). Preserve o `application.yml` local e não recrie o volume Docker.

## Homologação local

Com a branch v0.8 atualizada e a API `secure` iniciada na porta 8081 usando o banco `127.0.0.1:5434/livrostock`, abra `http://localhost:5173` e a tela **Biblioteca Digital**.

1. Verifique os nove livros digitais na primeira página.
2. Pesquise por `Spring` e confirme o filtro por título.
3. Filtre por categoria e experimente a paginação.
4. Clique em **Ler** e confirme que o PDF abre.
5. Use um usuário `LEITOR` para validar leitura e ausência de upload.
6. Reconfirme que o livro físico existente e seu estoque não foram alterados.

**Estado:** implementação em branch de revisão, sem afirmar validação no Windows até executar a homologação.
