#requires -Version 5.1
<#
.SYNOPSIS
Homologa API, JWT, integridade dos PDFs e Prometheus do LivroStock v1.0.
.DESCRIPTION
Somente leitura: nao altera PostgreSQL, PDFs ou configuracoes.
#>
[CmdletBinding()]
param(
    [string] $BaseUrl = 'http://127.0.0.1:8081',
    [ValidateRange(1,20)] [int] $TamanhoPagina = 20
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
$senhaSegura = $null
$credenciais = $null
$respostaLogin = $null
$cabecalho = $null

try {
    Write-Host '=== LivroStock v1.0 - homologacao somente-leitura ===' -ForegroundColor Cyan

    $saude = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -ErrorAction Stop
    if ($saude.status -ne 'UP') { throw "Actuator nao esta UP: $($saude.status)" }
    Write-Host '[OK] Actuator UP' -ForegroundColor Green

    $modo = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/mode" -ErrorAction Stop
    if ($modo.enabled -ne $true -or $modo.strategy -ne 'jwt') {
        throw 'Perfil secure / JWT nao habilitado'
    }
    Write-Host '[OK] Perfil secure com JWT' -ForegroundColor Green

    $senhaSegura = Read-Host 'Senha da conta bibliotecario' -AsSecureString
    $credenciais = @{
        usuario = 'bibliotecario'
        senha = [System.Net.NetworkCredential]::new('', $senhaSegura).Password
    } | ConvertTo-Json -Compress

    $respostaLogin = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -ContentType 'application/json' -Body $credenciais -ErrorAction Stop
    if ([string]::IsNullOrWhiteSpace([string]$respostaLogin.accessToken)) {
        throw 'JWT nao foi recebido'
    }
    if (-not (@($respostaLogin.roles) -contains 'BIBLIOTECARIO')) {
        throw 'A conta nao possui perfil BIBLIOTECARIO'
    }
    $cabecalho = @{ Authorization = "Bearer $($respostaLogin.accessToken)" }
    Write-Host '[OK] Login administrativo' -ForegroundColor Green

    $numero = 0
    $quantidadeTotal = 0
    $ok = 0
    $problemas = 0
    $paginas = 1

    do {
        if ($numero -ge 10000) { throw 'Limite de paginas excedido' }
        $pagina = Invoke-RestMethod -Uri "$BaseUrl/api/v1/acervo-digital/integridade?page=$numero&size=$TamanhoPagina" -Headers $cabecalho -ErrorAction Stop
        if ($numero -eq 0) {
            $quantidadeTotal = [int]$pagina.totalElements
            $paginas = [Math]::Max(1, [int]$pagina.totalPages)
        }
        $itens = @($pagina.content | Where-Object { $null -ne $_ })
        foreach ($pdf in $itens) {
            $titulo = [string]$pdf.titulo
            $estado = [string]$pdf.estado
            Write-Host ("{0}  [{1}]" -f $titulo, $estado)
            if ($estado -eq 'OK') { $ok++ } else { $problemas++ }
        }
        $numero++
    } while ($numero -lt $paginas)

    if (($ok + $problemas) -ne $quantidadeTotal) {
        throw "Contagem nao confere: $($ok + $problemas) de $quantidadeTotal"
    }
    Write-Host ("Auditoria: {0} PDF(s), {1} OK, {2} problema(s)" -f $quantidadeTotal, $ok, $problemas)
    if ($problemas -gt 0) {
        throw 'PDF(s) com problema. Nao altere os arquivos originais.'
    }

    try {
        $prometheus = Invoke-RestMethod -Uri "$BaseUrl/actuator/prometheus" -Headers $cabecalho -ErrorAction Stop
    }
    catch {
        $codigoHttp = $null
        if ($null -ne $_.Exception.Response) {
            $codigoHttp = [int]$_.Exception.Response.StatusCode
        }
        if ($codigoHttp -eq 403) {
            throw 'Prometheus HTTP 403 mesmo com bibliotecario autenticado. Confirme que a API na porta 8081 foi REINICIADA com a branch v1.0 (git branch --show-current) e que application.yml expõe metrics,prometheus. Nao libere metricas publicamente.'
        }
        if ($codigoHttp -eq 404) {
            throw 'Prometheus HTTP 404: endpoint nao registrado. Confirme a dependencia micrometer-registry-prometheus e management.endpoints.web.exposure.include no application.yml efetivo.'
        }
        throw
    }
    if (-not ([string]$prometheus -match 'livrostock_pdf_integridade_verificacoes_total')) {
        throw 'Metrica da auditoria ausente no Prometheus'
    }
    Write-Host '[OK] Prometheus e contador de integridade' -ForegroundColor Green

    $metricas = Invoke-RestMethod -Uri "$BaseUrl/actuator/metrics" -Headers $cabecalho -ErrorAction Stop
    if (-not $metricas.names) { throw 'Endpoint de metricas vazio' }
    Write-Host '[OK] Actuator /metrics' -ForegroundColor Green
    Write-Host ("HOMOLOGACAO V1.0 CONCLUIDA: {0}/{1} PDFs OK" -f $ok, $quantidadeTotal) -ForegroundColor Green
}
finally {
    Remove-Variable senhaSegura, credenciais, respostaLogin, cabecalho -ErrorAction SilentlyContinue
}
