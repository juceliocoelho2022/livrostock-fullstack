#requires -Version 5.1
<#
.SYNOPSIS
Testes sem Docker do verificador de ZIP. Seguro para executar no CI.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$base = Join-Path ([System.IO.Path]::GetTempPath()) ('livrostock-backup-test-' + [guid]::NewGuid().ToString('N'))
$pastas = Join-Path $base 'valid'
$corrompido = Join-Path $base 'corrupt'
$extra = Join-Path $base 'extra'
$verificador = Join-Path $PSScriptRoot 'verificar-backup.ps1'

try {
    foreach ($pasta in @($pastas, $corrompido, $extra)) {
        New-Item -ItemType Directory -Path $pasta -Force | Out-Null
    }
    New-Item -ItemType Directory -Path (Join-Path $pastas 'database') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $pastas 'pdfs') -Force | Out-Null
    $dump = Join-Path $pastas 'database\livrostock.dump'
    $pdf = Join-Path $pastas 'pdfs\exemplo.pdf'
    [System.IO.File]::WriteAllBytes($dump, [System.Text.Encoding]::UTF8.GetBytes('fixture-dump-nao-restauravel'))
    [System.IO.File]::WriteAllBytes($pdf, [System.Text.Encoding]::ASCII.GetBytes('%PDF-1.4' + [char]10 + 'fixture'))
    $itens = @(
        @{ caminho='database/livrostock.dump'; tamanhoBytes=[long](Get-Item $dump).Length;
            sha256=(Get-FileHash -LiteralPath $dump -Algorithm SHA256).Hash.ToLowerInvariant() },
        @{ caminho='pdfs/exemplo.pdf'; tamanhoBytes=[long](Get-Item $pdf).Length;
            sha256=(Get-FileHash -LiteralPath $pdf -Algorithm SHA256).Hash.ToLowerInvariant() }
    )
    $manifesto = @{ formato=1; aplicativo='LivroStock'; quantidadePdfs=1; banco='livrostock';
        criadoEmUtc=[DateTime]::UtcNow.ToString('o'); arquivos=$itens }
    $manifesto | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $pastas 'manifest.json') -Encoding UTF8

    $zipValido = Join-Path $base 'valido.zip'
    Compress-Archive -Path (Join-Path $pastas '*') -DestinationPath $zipValido
    & $verificador -Arquivo $zipValido
    if (-not $?) { throw 'Backup valido rejeitado pelo verificador' }

    Copy-Item -Path (Join-Path $pastas '*') -Destination $corrompido -Recurse
    [System.IO.File]::WriteAllBytes((Join-Path $corrompido 'pdfs\exemplo.pdf'),
        [System.Text.Encoding]::ASCII.GetBytes('conteudo-modificado'))
    $zipCorrompido = Join-Path $base 'corrompido.zip'
    Compress-Archive -Path (Join-Path $corrompido '*') -DestinationPath $zipCorrompido
    $rejeitou = $false
    try { & $verificador -Arquivo $zipCorrompido } catch { $rejeitou = $true }
    if (-not $rejeitou) { throw 'Falha: alteracao de PDF nao foi detectada' }

    Copy-Item -Path (Join-Path $pastas '*') -Destination $extra -Recurse
    [System.IO.File]::WriteAllText((Join-Path $extra 'arquivo-extra.txt'), 'nao listado')
    $zipExtra = Join-Path $base 'extra.zip'
    Compress-Archive -Path (Join-Path $extra '*') -DestinationPath $zipExtra
    $rejeitouExtra = $false
    try { & $verificador -Arquivo $zipExtra } catch { $rejeitouExtra = $true }
    if (-not $rejeitouExtra) { throw 'Falha: arquivo nao listado passou pela verificacao' }

    Write-Host 'TESTES OK: ZIP integro aceito, PDF corrompido e arquivo extra rejeitados.'
}
finally {
    if (Test-Path -LiteralPath $base) {
        Remove-Item -LiteralPath $base -Recurse -Force
    }
}
