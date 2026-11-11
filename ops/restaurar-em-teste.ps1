#requires -Version 5.1
<#
.SYNOPSIS
Restaura um backup LivroStock somente em banco NOVO e pasta PDF separada.
.DESCRIPTION
O banco livrostock original e a pasta ativa de PDFs nunca sao alterados.
Nao modifica a configuracao do backend nem inicia a aplicacao.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [string] $Arquivo,
    [string] $Destino = (Join-Path $env:USERPROFILE 'Documents\LivroStock-Restauracoes'),
    [string] $ComposeFile = (Join-Path $PSScriptRoot '..\backend\docker-compose.yml'),
    [string] $ContainerPostgres = '',
    [string] $UsuarioBanco = 'livrostock',
    [string] $BancoTeste = '',
    [switch] $ConfirmarCriacaoBancoTeste
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $ConfirmarCriacaoBancoTeste) {
    throw 'A restauracao cria um NOVO banco. Execute com -ConfirmarCriacaoBancoTeste para autorizar.'
}
if (-not (Test-Path -LiteralPath $Arquivo -PathType Leaf)) {
    throw "ZIP nao encontrado: $Arquivo"
}
if (-not (Test-Path -LiteralPath $ComposeFile -PathType Leaf)) {
    throw "Compose nao encontrado: $ComposeFile"
}
if ($UsuarioBanco -notmatch '^[a-zA-Z][a-zA-Z0-9_]{0,62}$') {
    throw 'Nome de usuario do banco invalido'
}
if ([string]::IsNullOrWhiteSpace($BancoTeste)) {
    $BancoTeste = 'livrostock_restore_' + [DateTime]::UtcNow.ToString('yyyyMMdd_HHmmss') +
        '_' + [guid]::NewGuid().ToString('N').Substring(0,6)
}
if ($BancoTeste -notmatch '^livrostock_restore_[a-z0-9_]{8,45}$' -or $BancoTeste.Length -gt 63) {
    throw 'Por seguranca o banco deve ter nome livrostock_restore_... e ate 63 caracteres'
}
if ($BancoTeste -eq 'livrostock') { throw 'Nao e permitido restaurar sobre o banco principal' }

# Autentica ZIP com SHA-256 ANTES de extrair e criar banco.
& (Join-Path $PSScriptRoot 'verificar-backup.ps1') -Arquivo $Arquivo
if (-not $?) { throw 'O backup falhou na verificacao de integridade' }

New-Item -ItemType Directory -Path $Destino -Force | Out-Null
$Destino = (Resolve-Path -LiteralPath $Destino).Path
$restauracao = Join-Path $Destino ('restauracao_' + [DateTime]::UtcNow.ToString('yyyyMMdd_HHmmss') +
    '_' + [guid]::NewGuid().ToString('N').Substring(0,8))
if (Test-Path -LiteralPath $restauracao) { throw 'Pasta de restauracao ja existe' }
New-Item -ItemType Directory -Path $restauracao -Force | Out-Null
$nomeTemporario = '/tmp/livrostock-restore-' + [guid]::NewGuid().ToString('N') + '.dump'
$container = ''

try {
    Expand-Archive -LiteralPath $Arquivo -DestinationPath $restauracao -ErrorAction Stop
    $dumpLocal = Join-Path $restauracao 'database\livrostock.dump'
    if (-not (Test-Path -LiteralPath $dumpLocal -PathType Leaf)) {
        throw 'Dump nao encontrado apos extracao'
    }
    $pdfs = Join-Path $restauracao 'pdfs'
    New-Item -ItemType Directory -Path $pdfs -Force | Out-Null

    if ([string]::IsNullOrWhiteSpace($ContainerPostgres)) {
        $container = (& docker compose -f $ComposeFile ps -q postgres | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or $container -notmatch '^[0-9a-fA-F]{12,64}$') {
            throw 'PostgreSQL nao encontrado no Compose. Informe -ContainerPostgres para outro container.'
        }
    }
    else {
        if ($ContainerPostgres -notmatch '^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}$') {
            throw 'Nome invalido de container PostgreSQL'
        }
        $container = $ContainerPostgres
    }
    $executando = (& docker inspect --format '{{.State.Running}}' $container | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $executando -ne 'true') {
        throw "Container nao esta em execucao: $container"
    }

    $sqlExiste = "SELECT 1 FROM pg_database WHERE datname = '$BancoTeste';"
    $existe = (& docker exec $container psql -U $UsuarioBanco -d postgres -tAc $sqlExiste | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel consultar os bancos existentes' }
    if ($existe -eq '1') {
        throw "Banco de teste ja existe: $BancoTeste. Nada foi sobrescrito."
    }

    $destinoDocker = ('{0}:{1}' -f $container, $nomeTemporario)
    & docker cp $dumpLocal $destinoDocker
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao copiar dump para o container' }

    # Recusa o dump vazio antes de criar o novo banco de teste.
    $listaDump = (& docker exec $container pg_restore -l $nomeTemporario | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'Dump custom PostgreSQL invalido ou ilegivel' }
    foreach ($tabela in @('livros','movimentacoes','exemplares_digitais','auditoria_eventos','usuarios')) {
        if ($listaDump -notmatch ("(?m)\bTABLE\s+public\s+{0}\s" -f [regex]::Escape($tabela))) {
            throw "RESTAURACAO RECUSADA: dump sem a tabela $tabela. Nenhum banco novo criado."
        }
    }

    # Nunca restaurar sobre o banco principal.
    & docker exec $container createdb -U $UsuarioBanco -O $UsuarioBanco $BancoTeste
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar banco isolado de restauracao' }

    Write-Host ("Restaurando apenas no banco isolado: {0}" -f $BancoTeste)
    & docker exec $container pg_restore -U $UsuarioBanco -d $BancoTeste --no-owner --no-privileges --exit-on-error $nomeTemporario
    if ($LASTEXITCODE -ne 0) {
        throw ("pg_restore falhou. Banco de TESTE {0} pode estar parcialmente preenchido; banco original intocado." -f $BancoTeste)
    }

    $sqlTabelas = "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('livros','movimentacoes','exemplares_digitais','auditoria_eventos','usuarios');"
    $quantidade = (& docker exec $container psql -U $UsuarioBanco -d $BancoTeste -tAc $sqlTabelas | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $quantidade -ne '5') {
        throw "Restauracao concluida parcialmente: conferir tabelas no banco de TESTE $BancoTeste."
    }

    $manifesto = Get-Content -LiteralPath (Join-Path $restauracao 'manifest.json') -Raw | ConvertFrom-Json
    $pdfCount = @((Get-ChildItem -LiteralPath $pdfs -Recurse -File -Force)).Count
    if ($pdfCount -ne [int]$manifesto.quantidadePdfs) {
        throw 'Quantidade de PDFs restaurados divergente do manifesto'
    }

    Write-Host ''
    Write-Host 'RESTAURACAO DE TESTE CONCLUIDA, SEM SOBRESCREVER OS DADOS ATUAIS.'
    Write-Host ("Banco de teste: {0}" -f $BancoTeste)
    Write-Host ("PDFs de teste: {0}" -f $pdfs)
    Write-Host 'Para testar no Spring Boot, use temporariamente DB_URL com o banco de teste'
    Write-Host 'e PDF_STORAGE_DIR com a pasta de teste. Nao altere o banco original.'
}
finally {
    if ($container -match '^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}$') {
        try { & docker exec $container rm -f $nomeTemporario 2>$null | Out-Null }
        catch { Write-Warning 'Nao foi possivel limpar o dump temporario no container.' }
    }
}
