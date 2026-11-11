#requires -Version 5.1
<#
.SYNOPSIS
Cria ZIP com dump PostgreSQL, acervo PDF e manifesto SHA-256.
.DESCRIPTION
Exige aplicacao parada para evitar divergencia entre referencias SQL e PDFs.
Nao altera tabelas, nao para o Docker e nao inclui .env ou chaves JWT.
#>
[CmdletBinding()]
param(
    [string] $Destino = (Join-Path $env:USERPROFILE 'Documents\LivroStock-Backups'),
    [string] $DiretorioPdf = (Join-Path $PSScriptRoot '..\backend\data\pdfs'),
    [string] $ComposeFile = (Join-Path $PSScriptRoot '..\backend\docker-compose.yml'),
    [string] $ContainerPostgres = '',
    [string] $Banco = 'livrostock',
    [string] $UsuarioBanco = 'livrostock',
    [switch] $ConfirmoAplicacaoParada,
    [switch] $PermitirAcervoVazio
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $ConfirmoAplicacaoParada) {
    throw 'Pare apenas o backend Java (mantenha PostgreSQL ligado) e execute com -ConfirmoAplicacaoParada.'
}
if ($Banco -notmatch '^[a-zA-Z][a-zA-Z0-9_]{0,62}$' -or
    $UsuarioBanco -notmatch '^[a-zA-Z][a-zA-Z0-9_]{0,62}$') {
    throw 'Nome do banco ou usuario invalido'
}
if (-not (Test-Path -LiteralPath $ComposeFile -PathType Leaf)) {
    throw "Docker Compose nao encontrado: $ComposeFile"
}
if (-not (Test-Path -LiteralPath $DiretorioPdf -PathType Container)) {
    if (-not $PermitirAcervoVazio) {
        throw "Pasta de PDFs nao encontrada: $DiretorioPdf. Corrija -DiretorioPdf ou declare -PermitirAcervoVazio."
    }
}

$diretorioPdfsExiste = Test-Path -LiteralPath $DiretorioPdf -PathType Container
if ($diretorioPdfsExiste) {
    $DiretorioPdf = (Resolve-Path -LiteralPath $DiretorioPdf).Path
    if (((Get-Item -LiteralPath $DiretorioPdf).Attributes -band
        [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw 'Diretorio de PDFs nao pode ser um link simbolico/junction'
    }
}
New-Item -ItemType Directory -Path $Destino -Force | Out-Null
$Destino = (Resolve-Path -LiteralPath $Destino).Path
$guid = [guid]::NewGuid().ToString('N')
$sufixo = [DateTime]::UtcNow.ToString('yyyyMMdd_HHmmss') + '_' + $guid.Substring(0,8)
$temporario = Join-Path $Destino ('.tmp-' + $guid)
$zipTemporario = Join-Path $Destino ('.tmp-' + $guid + '.zip')
$arquivoFinal = Join-Path $Destino ('livrostock_backup_' + $sufixo + '.zip')
$dumpContainer = '/tmp/livrostock-backup-' + $guid + '.dump'
$container = ''
$finalizado = $false

try {
    if ([string]::IsNullOrWhiteSpace($ContainerPostgres)) {
        $container = (& docker compose -f $ComposeFile ps -q postgres | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or $container -notmatch '^[0-9a-fA-F]{12,64} -ItemType Directory -Path (Join-Path $temporario 'database') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $temporario 'pdfs') -Force | Out-Null
    Write-Host 'Criando dump custom PostgreSQL no container...'
    & docker exec $container pg_dump -U $UsuarioBanco -d $Banco -Fc -f $dumpContainer
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump falhou. Confira o usuario/banco existentes.' }

    & docker exec $container pg_restore -l $dumpContainer | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'pg_restore nao conseguiu listar o dump criado.' }

    $origemDocker = ('{0}:{1}' -f $container, $dumpContainer)
    $dumpLocal = Join-Path $temporario 'database\livrostock.dump'
    & docker cp $origemDocker $dumpLocal
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $dumpLocal)) {
        throw 'Falha ao copiar o dump para a pasta temporaria.'
    }

    $arquivos = New-Object 'System.Collections.Generic.List[object]'
    $infoDump = Get-Item -LiteralPath $dumpLocal
    if ($infoDump.Length -eq 0 -or $infoDump.Length -ge 1900000000) {
        throw 'Dump vazio ou proximo do limite de 2 GB do Compress-Archive.'
    }
    $arquivos.Add(@{
        caminho = 'database/livrostock.dump'
        tamanhoBytes = [long]$infoDump.Length
        sha256 = (Get-FileHash -LiteralPath $dumpLocal -Algorithm SHA256).Hash.ToLowerInvariant()
    })

    $pdfs = @()
    if ($diretorioPdfsExiste) {
        $pdfs = @(Get-ChildItem -LiteralPath $DiretorioPdf -Recurse -Force -File)
    }
    if ($pdfs.Count -eq 0 -and -not $PermitirAcervoVazio) {
        throw 'Acervo PDF vazio. Para confirmar explicitamente, utilize -PermitirAcervoVazio.'
    }
    foreach ($item in $pdfs) {
        if ($item.Extension.ToLowerInvariant() -ne '.pdf') {
            throw "Arquivo nao PDF na pasta de acervo: $($item.Name). Revise a origem antes de fazer backup."
        }
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Link simbolico no acervo: $($item.Name)"
        }
        if ($item.Length -ge 1900000000) {
            throw "Arquivo muito grande para Compress-Archive: $($item.Name)"
        }

        $relativo = $item.FullName.Substring($DiretorioPdf.Length).TrimStart([char[]]@('\','/'))
        if ($relativo -match '(^|[\\/])\.\.([\\/]|$)') {
            throw 'Caminho inesperado no diretorio PDF'
        }
        $destinoPdf = Join-Path (Join-Path $temporario 'pdfs') $relativo
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destinoPdf) | Out-Null
        Copy-Item -LiteralPath $item.FullName -Destination $destinoPdf -ErrorAction Stop
        $arquivos.Add(@{
            caminho = 'pdfs/' + $relativo.Replace('\','/')
            tamanhoBytes = [long]$item.Length
            sha256 = (Get-FileHash -LiteralPath $destinoPdf -Algorithm SHA256).Hash.ToLowerInvariant()
        })
    }

    $manifesto = @{
        formato = 1
        aplicativo = 'LivroStock'
        criadoEmUtc = [DateTime]::UtcNow.ToString('o')
        quantidadePdfs = $pdfs.Count
        banco = $Banco
        arquivos = $arquivos.ToArray()
    }
    $manifesto | ConvertTo-Json -Depth 8 |
        Set-Content -LiteralPath (Join-Path $temporario 'manifest.json') -Encoding UTF8

    Write-Host ("Compactando banco e {0} PDFs..." -f $pdfs.Count)
    Compress-Archive -Path (Join-Path $temporario '*') -DestinationPath $zipTemporario -CompressionLevel Optimal
    if (-not (Test-Path -LiteralPath $zipTemporario -PathType Leaf)) {
        throw 'Falha ao gerar ZIP.'
    }

    & (Join-Path $PSScriptRoot 'verificar-backup.ps1') -Arquivo $zipTemporario
    if (-not $?) { throw 'Verificacao do backup falhou.' }

    Move-Item -LiteralPath $zipTemporario -Destination $arquivoFinal -ErrorAction Stop
    $finalizado = $true
    Write-Host ("BACKUP CRIADO: {0}" -f $arquivoFinal)
    Write-Host 'Guarde este arquivo em unidade protegida, fora do repositorio Git.'
}
finally {
    if ($container -match '^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127} {
        & docker exec $container rm -f $dumpContainer 2>$null | Out-Null
    }
    if (Test-Path -LiteralPath $temporario) {
        Remove-Item -LiteralPath $temporario -Recurse -Force
    }
    if (-not $finalizado -and (Test-Path -LiteralPath $zipTemporario)) {
        Remove-Item -LiteralPath $zipTemporario -Force
    }
}
) {
            throw 'PostgreSQL nao encontrado no Compose. Informe -ContainerPostgres para selecionar outro container.'
        }
    }
    else {
        if ($ContainerPostgres -notmatch '^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127} -ItemType Directory -Path (Join-Path $temporario 'database') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $temporario 'pdfs') -Force | Out-Null
    Write-Host 'Criando dump custom PostgreSQL no container...'
    & docker exec $container pg_dump -U $UsuarioBanco -d $Banco -Fc -f $dumpContainer
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump falhou. Confira o usuario/banco existentes.' }

    & docker exec $container pg_restore -l $dumpContainer | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'pg_restore nao conseguiu listar o dump criado.' }

    $origemDocker = ('{0}:{1}' -f $container, $dumpContainer)
    $dumpLocal = Join-Path $temporario 'database\livrostock.dump'
    & docker cp $origemDocker $dumpLocal
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $dumpLocal)) {
        throw 'Falha ao copiar o dump para a pasta temporaria.'
    }

    $arquivos = New-Object 'System.Collections.Generic.List[object]'
    $infoDump = Get-Item -LiteralPath $dumpLocal
    if ($infoDump.Length -eq 0 -or $infoDump.Length -ge 1900000000) {
        throw 'Dump vazio ou proximo do limite de 2 GB do Compress-Archive.'
    }
    $arquivos.Add(@{
        caminho = 'database/livrostock.dump'
        tamanhoBytes = [long]$infoDump.Length
        sha256 = (Get-FileHash -LiteralPath $dumpLocal -Algorithm SHA256).Hash.ToLowerInvariant()
    })

    $pdfs = @()
    if ($diretorioPdfsExiste) {
        $pdfs = @(Get-ChildItem -LiteralPath $DiretorioPdf -Recurse -Force -File)
    }
    if ($pdfs.Count -eq 0 -and -not $PermitirAcervoVazio) {
        throw 'Acervo PDF vazio. Para confirmar explicitamente, utilize -PermitirAcervoVazio.'
    }
    foreach ($item in $pdfs) {
        if ($item.Extension.ToLowerInvariant() -ne '.pdf') {
            throw "Arquivo nao PDF na pasta de acervo: $($item.Name). Revise a origem antes de fazer backup."
        }
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Link simbolico no acervo: $($item.Name)"
        }
        if ($item.Length -ge 1900000000) {
            throw "Arquivo muito grande para Compress-Archive: $($item.Name)"
        }

        $relativo = $item.FullName.Substring($DiretorioPdf.Length).TrimStart([char[]]@('\','/'))
        if ($relativo -match '(^|[\\/])\.\.([\\/]|$)') {
            throw 'Caminho inesperado no diretorio PDF'
        }
        $destinoPdf = Join-Path (Join-Path $temporario 'pdfs') $relativo
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destinoPdf) | Out-Null
        Copy-Item -LiteralPath $item.FullName -Destination $destinoPdf -ErrorAction Stop
        $arquivos.Add(@{
            caminho = 'pdfs/' + $relativo.Replace('\','/')
            tamanhoBytes = [long]$item.Length
            sha256 = (Get-FileHash -LiteralPath $destinoPdf -Algorithm SHA256).Hash.ToLowerInvariant()
        })
    }

    $manifesto = @{
        formato = 1
        aplicativo = 'LivroStock'
        criadoEmUtc = [DateTime]::UtcNow.ToString('o')
        quantidadePdfs = $pdfs.Count
        banco = $Banco
        arquivos = $arquivos.ToArray()
    }
    $manifesto | ConvertTo-Json -Depth 8 |
        Set-Content -LiteralPath (Join-Path $temporario 'manifest.json') -Encoding UTF8

    Write-Host ("Compactando banco e {0} PDFs..." -f $pdfs.Count)
    Compress-Archive -Path (Join-Path $temporario '*') -DestinationPath $zipTemporario -CompressionLevel Optimal
    if (-not (Test-Path -LiteralPath $zipTemporario -PathType Leaf)) {
        throw 'Falha ao gerar ZIP.'
    }

    & (Join-Path $PSScriptRoot 'verificar-backup.ps1') -Arquivo $zipTemporario
    if (-not $?) { throw 'Verificacao do backup falhou.' }

    Move-Item -LiteralPath $zipTemporario -Destination $arquivoFinal -ErrorAction Stop
    $finalizado = $true
    Write-Host ("BACKUP CRIADO: {0}" -f $arquivoFinal)
    Write-Host 'Guarde este arquivo em unidade protegida, fora do repositorio Git.'
}
finally {
    if ($container -match '^[0-9a-fA-F]{12,64}$') {
        & docker exec $container rm -f $dumpContainer 2>$null | Out-Null
    }
    if (Test-Path -LiteralPath $temporario) {
        Remove-Item -LiteralPath $temporario -Recurse -Force
    }
    if (-not $finalizado -and (Test-Path -LiteralPath $zipTemporario)) {
        Remove-Item -LiteralPath $zipTemporario -Force
    }
}
) {
            throw 'Nome invalido de container PostgreSQL'
        }
        $container = $ContainerPostgres
    }
    $executando = (& docker inspect --format '{{.State.Running}}' $container | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $executando -ne 'true') {
        throw "Container PostgreSQL nao esta em execucao: $container"
    }
    & docker exec $container pg_isready -U $UsuarioBanco -d $Banco | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Banco $Banco indisponivel no container $container"
    }

    # Impede backup silencioso do segundo Postgres vazio (backend-postgres-1).
    $sqlTabelas = "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('livros','movimentacoes','exemplares_digitais','auditoria_eventos','usuarios');"
    $tabelas = (& docker exec $container psql -U $UsuarioBanco -d $Banco -tAc $sqlTabelas | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $tabelas -ne '5') {
        throw "BACKUP RECUSADO: banco $Banco no container $container nao possui as cinco tabelas LivroStock v0.5. Banco possivelmente vazio/incorreto. Nenhum ZIP criado."
    }
    Write-Host ("Origem confirmada: container={0}, banco={1}, tabelas=5" -f $container,$Banco)

    New-Item -ItemType Directory -Path (Join-Path $temporario 'database') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $temporario 'pdfs') -Force | Out-Null
    Write-Host 'Criando dump custom PostgreSQL no container...'
    & docker exec $container pg_dump -U $UsuarioBanco -d $Banco -Fc -f $dumpContainer
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump falhou. Confira o usuario/banco existentes.' }

    & docker exec $container pg_restore -l $dumpContainer | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'pg_restore nao conseguiu listar o dump criado.' }

    $origemDocker = ('{0}:{1}' -f $container, $dumpContainer)
    $dumpLocal = Join-Path $temporario 'database\livrostock.dump'
    & docker cp $origemDocker $dumpLocal
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $dumpLocal)) {
        throw 'Falha ao copiar o dump para a pasta temporaria.'
    }

    $arquivos = New-Object 'System.Collections.Generic.List[object]'
    $infoDump = Get-Item -LiteralPath $dumpLocal
    if ($infoDump.Length -eq 0 -or $infoDump.Length -ge 1900000000) {
        throw 'Dump vazio ou proximo do limite de 2 GB do Compress-Archive.'
    }
    $arquivos.Add(@{
        caminho = 'database/livrostock.dump'
        tamanhoBytes = [long]$infoDump.Length
        sha256 = (Get-FileHash -LiteralPath $dumpLocal -Algorithm SHA256).Hash.ToLowerInvariant()
    })

    $pdfs = @()
    if ($diretorioPdfsExiste) {
        $pdfs = @(Get-ChildItem -LiteralPath $DiretorioPdf -Recurse -Force -File)
    }
    if ($pdfs.Count -eq 0 -and -not $PermitirAcervoVazio) {
        throw 'Acervo PDF vazio. Para confirmar explicitamente, utilize -PermitirAcervoVazio.'
    }
    foreach ($item in $pdfs) {
        if ($item.Extension.ToLowerInvariant() -ne '.pdf') {
            throw "Arquivo nao PDF na pasta de acervo: $($item.Name). Revise a origem antes de fazer backup."
        }
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Link simbolico no acervo: $($item.Name)"
        }
        if ($item.Length -ge 1900000000) {
            throw "Arquivo muito grande para Compress-Archive: $($item.Name)"
        }

        $relativo = $item.FullName.Substring($DiretorioPdf.Length).TrimStart([char[]]@('\','/'))
        if ($relativo -match '(^|[\\/])\.\.([\\/]|$)') {
            throw 'Caminho inesperado no diretorio PDF'
        }
        $destinoPdf = Join-Path (Join-Path $temporario 'pdfs') $relativo
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destinoPdf) | Out-Null
        Copy-Item -LiteralPath $item.FullName -Destination $destinoPdf -ErrorAction Stop
        $arquivos.Add(@{
            caminho = 'pdfs/' + $relativo.Replace('\','/')
            tamanhoBytes = [long]$item.Length
            sha256 = (Get-FileHash -LiteralPath $destinoPdf -Algorithm SHA256).Hash.ToLowerInvariant()
        })
    }

    $manifesto = @{
        formato = 1
        aplicativo = 'LivroStock'
        criadoEmUtc = [DateTime]::UtcNow.ToString('o')
        quantidadePdfs = $pdfs.Count
        banco = $Banco
        arquivos = $arquivos.ToArray()
    }
    $manifesto | ConvertTo-Json -Depth 8 |
        Set-Content -LiteralPath (Join-Path $temporario 'manifest.json') -Encoding UTF8

    Write-Host ("Compactando banco e {0} PDFs..." -f $pdfs.Count)
    Compress-Archive -Path (Join-Path $temporario '*') -DestinationPath $zipTemporario -CompressionLevel Optimal
    if (-not (Test-Path -LiteralPath $zipTemporario -PathType Leaf)) {
        throw 'Falha ao gerar ZIP.'
    }

    & (Join-Path $PSScriptRoot 'verificar-backup.ps1') -Arquivo $zipTemporario
    if (-not $?) { throw 'Verificacao do backup falhou.' }

    Move-Item -LiteralPath $zipTemporario -Destination $arquivoFinal -ErrorAction Stop
    $finalizado = $true
    Write-Host ("BACKUP CRIADO: {0}" -f $arquivoFinal)
    Write-Host 'Guarde este arquivo em unidade protegida, fora do repositorio Git.'
}
finally {
    if ($container -match '^[0-9a-fA-F]{12,64}$') {
        & docker exec $container rm -f $dumpContainer 2>$null | Out-Null
    }
    if (Test-Path -LiteralPath $temporario) {
        Remove-Item -LiteralPath $temporario -Recurse -Force
    }
    if (-not $finalizado -and (Test-Path -LiteralPath $zipTemporario)) {
        Remove-Item -LiteralPath $zipTemporario -Force
    }
}
) {
        & docker exec $container rm -f $dumpContainer 2>$null | Out-Null
    }
    if (Test-Path -LiteralPath $temporario) {
        Remove-Item -LiteralPath $temporario -Recurse -Force
    }
    if (-not $finalizado -and (Test-Path -LiteralPath $zipTemporario)) {
        Remove-Item -LiteralPath $zipTemporario -Force
    }
}
) {
            throw 'PostgreSQL nao encontrado no Compose. Informe -ContainerPostgres para selecionar outro container.'
        }
    }
    else {
        if ($ContainerPostgres -notmatch '^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127} -ItemType Directory -Path (Join-Path $temporario 'database') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $temporario 'pdfs') -Force | Out-Null
    Write-Host 'Criando dump custom PostgreSQL no container...'
    & docker exec $container pg_dump -U $UsuarioBanco -d $Banco -Fc -f $dumpContainer
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump falhou. Confira o usuario/banco existentes.' }

    & docker exec $container pg_restore -l $dumpContainer | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'pg_restore nao conseguiu listar o dump criado.' }

    $origemDocker = ('{0}:{1}' -f $container, $dumpContainer)
    $dumpLocal = Join-Path $temporario 'database\livrostock.dump'
    & docker cp $origemDocker $dumpLocal
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $dumpLocal)) {
        throw 'Falha ao copiar o dump para a pasta temporaria.'
    }

    $arquivos = New-Object 'System.Collections.Generic.List[object]'
    $infoDump = Get-Item -LiteralPath $dumpLocal
    if ($infoDump.Length -eq 0 -or $infoDump.Length -ge 1900000000) {
        throw 'Dump vazio ou proximo do limite de 2 GB do Compress-Archive.'
    }
    $arquivos.Add(@{
        caminho = 'database/livrostock.dump'
        tamanhoBytes = [long]$infoDump.Length
        sha256 = (Get-FileHash -LiteralPath $dumpLocal -Algorithm SHA256).Hash.ToLowerInvariant()
    })

    $pdfs = @()
    if ($diretorioPdfsExiste) {
        $pdfs = @(Get-ChildItem -LiteralPath $DiretorioPdf -Recurse -Force -File)
    }
    if ($pdfs.Count -eq 0 -and -not $PermitirAcervoVazio) {
        throw 'Acervo PDF vazio. Para confirmar explicitamente, utilize -PermitirAcervoVazio.'
    }
    foreach ($item in $pdfs) {
        if ($item.Extension.ToLowerInvariant() -ne '.pdf') {
            throw "Arquivo nao PDF na pasta de acervo: $($item.Name). Revise a origem antes de fazer backup."
        }
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Link simbolico no acervo: $($item.Name)"
        }
        if ($item.Length -ge 1900000000) {
            throw "Arquivo muito grande para Compress-Archive: $($item.Name)"
        }

        $relativo = $item.FullName.Substring($DiretorioPdf.Length).TrimStart([char[]]@('\','/'))
        if ($relativo -match '(^|[\\/])\.\.([\\/]|$)') {
            throw 'Caminho inesperado no diretorio PDF'
        }
        $destinoPdf = Join-Path (Join-Path $temporario 'pdfs') $relativo
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destinoPdf) | Out-Null
        Copy-Item -LiteralPath $item.FullName -Destination $destinoPdf -ErrorAction Stop
        $arquivos.Add(@{
            caminho = 'pdfs/' + $relativo.Replace('\','/')
            tamanhoBytes = [long]$item.Length
            sha256 = (Get-FileHash -LiteralPath $destinoPdf -Algorithm SHA256).Hash.ToLowerInvariant()
        })
    }

    $manifesto = @{
        formato = 1
        aplicativo = 'LivroStock'
        criadoEmUtc = [DateTime]::UtcNow.ToString('o')
        quantidadePdfs = $pdfs.Count
        banco = $Banco
        arquivos = $arquivos.ToArray()
    }
    $manifesto | ConvertTo-Json -Depth 8 |
        Set-Content -LiteralPath (Join-Path $temporario 'manifest.json') -Encoding UTF8

    Write-Host ("Compactando banco e {0} PDFs..." -f $pdfs.Count)
    Compress-Archive -Path (Join-Path $temporario '*') -DestinationPath $zipTemporario -CompressionLevel Optimal
    if (-not (Test-Path -LiteralPath $zipTemporario -PathType Leaf)) {
        throw 'Falha ao gerar ZIP.'
    }

    & (Join-Path $PSScriptRoot 'verificar-backup.ps1') -Arquivo $zipTemporario
    if (-not $?) { throw 'Verificacao do backup falhou.' }

    Move-Item -LiteralPath $zipTemporario -Destination $arquivoFinal -ErrorAction Stop
    $finalizado = $true
    Write-Host ("BACKUP CRIADO: {0}" -f $arquivoFinal)
    Write-Host 'Guarde este arquivo em unidade protegida, fora do repositorio Git.'
}
finally {
    if ($container -match '^[0-9a-fA-F]{12,64}$') {
        & docker exec $container rm -f $dumpContainer 2>$null | Out-Null
    }
    if (Test-Path -LiteralPath $temporario) {
        Remove-Item -LiteralPath $temporario -Recurse -Force
    }
    if (-not $finalizado -and (Test-Path -LiteralPath $zipTemporario)) {
        Remove-Item -LiteralPath $zipTemporario -Force
    }
}
) {
            throw 'Nome invalido de container PostgreSQL'
        }
        $container = $ContainerPostgres
    }
    $executando = (& docker inspect --format '{{.State.Running}}' $container | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $executando -ne 'true') {
        throw "Container PostgreSQL nao esta em execucao: $container"
    }
    & docker exec $container pg_isready -U $UsuarioBanco -d $Banco | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Banco $Banco indisponivel no container $container"
    }

    # Impede backup silencioso do segundo Postgres vazio (backend-postgres-1).
    $sqlTabelas = "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('livros','movimentacoes','exemplares_digitais','auditoria_eventos','usuarios');"
    $tabelas = (& docker exec $container psql -U $UsuarioBanco -d $Banco -tAc $sqlTabelas | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $tabelas -ne '5') {
        throw "BACKUP RECUSADO: banco $Banco no container $container nao possui as cinco tabelas LivroStock v0.5. Banco possivelmente vazio/incorreto. Nenhum ZIP criado."
    }
    Write-Host ("Origem confirmada: container={0}, banco={1}, tabelas=5" -f $container,$Banco)

    New-Item -ItemType Directory -Path (Join-Path $temporario 'database') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $temporario 'pdfs') -Force | Out-Null
    Write-Host 'Criando dump custom PostgreSQL no container...'
    & docker exec $container pg_dump -U $UsuarioBanco -d $Banco -Fc -f $dumpContainer
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump falhou. Confira o usuario/banco existentes.' }

    & docker exec $container pg_restore -l $dumpContainer | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'pg_restore nao conseguiu listar o dump criado.' }

    $origemDocker = ('{0}:{1}' -f $container, $dumpContainer)
    $dumpLocal = Join-Path $temporario 'database\livrostock.dump'
    & docker cp $origemDocker $dumpLocal
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $dumpLocal)) {
        throw 'Falha ao copiar o dump para a pasta temporaria.'
    }

    $arquivos = New-Object 'System.Collections.Generic.List[object]'
    $infoDump = Get-Item -LiteralPath $dumpLocal
    if ($infoDump.Length -eq 0 -or $infoDump.Length -ge 1900000000) {
        throw 'Dump vazio ou proximo do limite de 2 GB do Compress-Archive.'
    }
    $arquivos.Add(@{
        caminho = 'database/livrostock.dump'
        tamanhoBytes = [long]$infoDump.Length
        sha256 = (Get-FileHash -LiteralPath $dumpLocal -Algorithm SHA256).Hash.ToLowerInvariant()
    })

    $pdfs = @()
    if ($diretorioPdfsExiste) {
        $pdfs = @(Get-ChildItem -LiteralPath $DiretorioPdf -Recurse -Force -File)
    }
    if ($pdfs.Count -eq 0 -and -not $PermitirAcervoVazio) {
        throw 'Acervo PDF vazio. Para confirmar explicitamente, utilize -PermitirAcervoVazio.'
    }
    foreach ($item in $pdfs) {
        if ($item.Extension.ToLowerInvariant() -ne '.pdf') {
            throw "Arquivo nao PDF na pasta de acervo: $($item.Name). Revise a origem antes de fazer backup."
        }
        if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw "Link simbolico no acervo: $($item.Name)"
        }
        if ($item.Length -ge 1900000000) {
            throw "Arquivo muito grande para Compress-Archive: $($item.Name)"
        }

        $relativo = $item.FullName.Substring($DiretorioPdf.Length).TrimStart([char[]]@('\','/'))
        if ($relativo -match '(^|[\\/])\.\.([\\/]|$)') {
            throw 'Caminho inesperado no diretorio PDF'
        }
        $destinoPdf = Join-Path (Join-Path $temporario 'pdfs') $relativo
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destinoPdf) | Out-Null
        Copy-Item -LiteralPath $item.FullName -Destination $destinoPdf -ErrorAction Stop
        $arquivos.Add(@{
            caminho = 'pdfs/' + $relativo.Replace('\','/')
            tamanhoBytes = [long]$item.Length
            sha256 = (Get-FileHash -LiteralPath $destinoPdf -Algorithm SHA256).Hash.ToLowerInvariant()
        })
    }

    $manifesto = @{
        formato = 1
        aplicativo = 'LivroStock'
        criadoEmUtc = [DateTime]::UtcNow.ToString('o')
        quantidadePdfs = $pdfs.Count
        banco = $Banco
        arquivos = $arquivos.ToArray()
    }
    $manifesto | ConvertTo-Json -Depth 8 |
        Set-Content -LiteralPath (Join-Path $temporario 'manifest.json') -Encoding UTF8

    Write-Host ("Compactando banco e {0} PDFs..." -f $pdfs.Count)
    Compress-Archive -Path (Join-Path $temporario '*') -DestinationPath $zipTemporario -CompressionLevel Optimal
    if (-not (Test-Path -LiteralPath $zipTemporario -PathType Leaf)) {
        throw 'Falha ao gerar ZIP.'
    }

    & (Join-Path $PSScriptRoot 'verificar-backup.ps1') -Arquivo $zipTemporario
    if (-not $?) { throw 'Verificacao do backup falhou.' }

    Move-Item -LiteralPath $zipTemporario -Destination $arquivoFinal -ErrorAction Stop
    $finalizado = $true
    Write-Host ("BACKUP CRIADO: {0}" -f $arquivoFinal)
    Write-Host 'Guarde este arquivo em unidade protegida, fora do repositorio Git.'
}
finally {
    if ($container -match '^[0-9a-fA-F]{12,64}$') {
        & docker exec $container rm -f $dumpContainer 2>$null | Out-Null
    }
    if (Test-Path -LiteralPath $temporario) {
        Remove-Item -LiteralPath $temporario -Recurse -Force
    }
    if (-not $finalizado -and (Test-Path -LiteralPath $zipTemporario)) {
        Remove-Item -LiteralPath $zipTemporario -Force
    }
}
