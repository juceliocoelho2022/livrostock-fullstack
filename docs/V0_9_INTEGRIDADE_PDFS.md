# LivroStock v0.9 — integridade e auditoria dos PDFs

## Objetivo

A v0.8 já autentica via JWT, lista os livros digitais e permite leitura no navegador. A v0.9 acrescenta **verificação de integridade** dos arquivos PDF antes da leitura e uma auditoria somente para **BIBLIOTECARIO**. Ela é construída sobre a branch `feature/v0.8-api-acervo-digital`.

**Não cria migrations, não substitui PDFs e não modifica os registros existentes.**

## Como funciona

O upload da Biblioteca Digital já salva no PostgreSQL:

- `tamanho_bytes` — tamanho do arquivo no momento da importação;
- `sha256` — hash do conteúdo binário no momento da importação;
- `nome_armazenado` — nome interno do arquivo, sem exposição na API pública.

Ao solicitar um PDF pelo endpoint de leitura original, a aplicação:

1. Verifica se o caminho está dentro do diretório autorizado de armazenamento.
2. Rejeita arquivos ausentes e links simbólicos.
3. Compara o tamanho atual com o registrado no PostgreSQL.
4. Calcula novamente o **SHA-256** e compara com o hash original.
5. Somente então retorna o PDF ao leitor.

Essa checagem detecta alterações acidentais ou corrupção de conteúdo; não substitui **antivírus**, licenciamento, controle operacional de acesso ao storage ou proteção contra alteração do arquivo entre a verificação e a transmissão.

## Auditoria de integridade

```http
GET /api/v1/acervo-digital/integridade?page=0&size=10
Authorization: Bearer <JWT de bibliotecario>
```

- Perfil Spring: **secure**.
- Permissão: **BIBLIOTECARIO**.
- Paginação: `page` de 0 a 10000; `size` de 1 a 20.
- Resposta: página com `livroId`, `exemplarId`, `titulo`, `nomeOriginal`, `tamanhoEsperadoBytes` e `estado`.
- `Cache-Control: no-store`, sem caminho interno, nome físico ou credenciais.
- Retorna apenas PDFs de livros **exclusivamente digitais ativos**, seguindo o filtro da v0.8.

### Estados

| Estado | Significado | Ação |
| --- | --- | --- |
| `OK` | Tamanho e SHA-256 conferem | Arquivo disponível |
| `AUSENTE` | Arquivo não encontrado como arquivo regular | Conferir pasta e backup |
| `DIVERGENTE` | Tamanho ou SHA-256 incompatível | Bloquear leitura; examinar backup |
| `ERRO_LEITURA` | O sistema não conseguiu verificar o arquivo | Inspecionar permissões e disco |

Se a leitura normal encontrar `AUSENTE`, responde **404**; se encontrar `DIVERGENTE` ou `ERRO_LEITURA`, responde **409** e não entrega o arquivo. Regras existentes de JWT permanecem válidas.

**Não tente recuperar ou corrigir PDFs escrevendo diretamente no banco de dados.** Caso necessário, restaure uma cópia íntegra em ambiente isolado usando os procedimentos da v0.6.

## Homologação local no Windows

Use o backup real confirmado para o banco `livrostock` no container `livrostock-api-postgres-1`, e a pasta com os **10 PDFs**. Confirme `PDF_STORAGE_DIR` antes de iniciar a API. Não execute `docker compose down -v` nem descarte o `application.yml` modificado localmente.

Com o backend `secure` na porta 8081, faça login como `bibliotecario` na interface. Em PowerShell, sem compartilhar o token, execute:

```powershell
$usuario = "bibliotecario"
$senha = Read-Host "Senha do bibliotecario" -AsSecureString
$corpo = @{ usuario = $usuario; senha = [System.Net.NetworkCredential]::new("", $senha).Password } | ConvertTo-Json
try {
    $login = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $corpo -ErrorAction Stop
    if (-not $login.accessToken) { throw "JWT ausente" }
    $headers = @{ Authorization = "Bearer $($login.accessToken)" }
    $pagina = Invoke-RestMethod -Uri "http://127.0.0.1:8081/api/v1/acervo-digital/integridade?page=0&size=20" -Headers $headers -ErrorAction Stop
    $pagina.content | Select-Object titulo, nomeOriginal, estado | Format-Table -AutoSize
    Write-Host "Total de PDFs digitais verificados: $($pagina.totalElements)"
} finally {
    Remove-Variable senha, corpo, login, headers -ErrorAction SilentlyContinue
}
```

**Esperado:** os 9 PDFs dos livros digitais ativos aparecem com estado `OK`. A pasta possui 10 PDFs porque pode incluir um exemplar vinculado a livro físico, não contemplado pelo índice digital.

Não altere os arquivos reais para simular corrupção. Os testes automatizados criam seus **próprios PDFs descartáveis** em pasta `target/` e banco de integração isolado.

## Testes e segurança

- `BibliotecaDigitalServiceTest`: tamanho, hash correto, corrupção com tamanho idêntico e arquivo ausente.
- `IntegridadePdfIntegrationTest`: JWT e autorização, verificação de arquivo íntegro, adulteração, arquivo ausente e bloqueio de leitura com PostgreSQL descartável.
- CI na branch v0.9: Maven Java 21, PostgreSQL 16/Flyway, React/TypeScript, testes de backup e restore.

**Limitação deliberada:** a auditoria é sob demanda e paginada, para evitar ler indiscriminadamente grandes volumes de PDFs. Em arquivos alterados depois da conferência e antes da transferência ainda existe uma pequena janela de corrida do sistema de arquivos. Em um futuro deploy externo, usar armazenamento imutável/versionado e observabilidade operacional.
