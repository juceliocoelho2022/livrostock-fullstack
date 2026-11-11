#requires -Version 5.1
<#
.SYNOPSIS
Verifica integridade SHA-256 e estrutura de um backup do LivroStock.
.DESCRIPTION
Somente leitura. Nao extrai arquivos e nao conecta ao PostgreSQL.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Arquivo
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Arquivo -PathType Leaf)) {
    throw "Backup nao encontrado: $Arquivo"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Arquivo).Path)
try {
    $entradas = @{}
    foreach ($entrada in $zip.Entries) {
        # Compress-Archive no Windows pode escrever separadores de pasta com barra invertida.
        # Normalizar antes de validar para nao rejeitar um backup legitimo.
        $nome = $entrada.FullName.Replace('\','/')
        if ($nome -match '(^/|^[A-Za-z]:|(^|/)\.\.(/|$))') {
            throw "Caminho inseguro dentro do ZIP: $nome"
        }
        if ($nome.EndsWith('/')) { continue }
        if ($entradas.ContainsKey($nome)) { throw "Entrada duplicada no ZIP: $nome" }
        $entradas[$nome] = $entrada
    }

    if (-not $entradas.ContainsKey('manifest.json')) {
        throw 'Backup invalido: manifest.json ausente'
    }

    $leitor = New-Object System.IO.StreamReader($entradas['manifest.json'].Open(),
        [System.Text.Encoding]::UTF8, $true)
    try {
        $manifesto = $leitor.ReadToEnd() | ConvertFrom-Json
    }
    finally {
        $leitor.Dispose()
    }

    if ($null -eq $manifesto -or [int]$manifesto.formato -ne 1) {
        throw 'Versao de formato do manifesto nao suportada'
    }
    $arquivos = @($manifesto.arquivos)
    if ($arquivos.Count -lt 1) { throw 'Manifesto sem arquivos' }

    $esperados = @{}
    foreach ($item in $arquivos) {
        $nome = [string]$item.caminho
        if (($nome -ne 'database/livrostock.dump') -and
            (-not $nome.StartsWith('pdfs/', [System.StringComparison]::Ordinal))) {
            throw "Caminho nao permitido no manifesto: $nome"
        }
        if ($nome -match '(^/|^[A-Za-z]:|\\|(^|/)\.\.(/|$))') {
            throw "Caminho inseguro no manifesto: $nome"
        }
        if ($esperados.ContainsKey($nome)) { throw "Arquivo duplicado no manifesto: $nome" }
        if (-not $entradas.ContainsKey($nome)) { throw "Arquivo ausente no ZIP: $nome" }
        if ([string]$item.sha256 -notmatch '^[a-fA-F0-9]{64}$') {
            throw "SHA-256 invalido para $nome"
        }
        if ([long]$item.tamanhoBytes -lt 0) { throw "Tamanho invalido para $nome" }
        $esperados[$nome] = $item
    }
    if (-not $esperados.ContainsKey('database/livrostock.dump')) {
        throw 'Backup sem o dump PostgreSQL'
    }
    foreach ($nome in $entradas.Keys) {
        if ($nome -ne 'manifest.json' -and -not $esperados.ContainsKey($nome)) {
            throw "Arquivo nao listado no manifesto: $nome"
        }
    }

    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        foreach ($nome in $esperados.Keys) {
            $entrada = $entradas[$nome]
            $item = $esperados[$nome]
            if ($entrada.Length -ne [long]$item.tamanhoBytes) {
                throw "Tamanho divergente: $nome"
            }
            $stream = $entrada.Open()
            try {
                $calculado = [BitConverter]::ToString($sha.ComputeHash($stream)).
                    Replace('-', '').ToLowerInvariant()
            }
            finally {
                $stream.Dispose()
            }
            if ($calculado -ne ([string]$item.sha256).ToLowerInvariant()) {
                throw "SHA-256 divergente: $nome"
            }
        }
    }
    finally {
        $sha.Dispose()
    }

    $pdfs = @($arquivos | Where-Object { $_.caminho -like 'pdfs/*' })
    if ($pdfs.Count -ne [int]$manifesto.quantidadePdfs) {
        throw 'Quantidade de PDFs divergente no manifesto'
    }
    Write-Host ("BACKUP INTEGRO: {0} arquivos, {1} PDFs, verificado via SHA-256." -f
        $arquivos.Count, $pdfs.Count)
}
finally {
    $zip.Dispose()
}
