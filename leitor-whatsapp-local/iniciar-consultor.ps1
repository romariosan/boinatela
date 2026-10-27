# Sobe só o CHIP CONSULTOR com logs em arquivos separados.
# Nunca use o título "CHIP CONSULTOR" como -FilePath do Start-Process.
$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $raiz

if (-not (Test-Path (Join-Path $raiz '.env'))) {
    Write-Host 'Falta o arquivo .env. Copie .env.example e preencha os tokens.'
    exit 1
}

$node = 'C:\Program Files\nodejs\node.exe'
if (-not (Test-Path $node)) {
    $node = (Get-Command node -ErrorAction Stop).Source
}

Remove-Item Env:PUPPETEER_CACHE_DIR -ErrorAction SilentlyContinue
Remove-Item Env:PUPPETEER_TMP_DIR -ErrorAction SilentlyContinue

$saida = Join-Path $raiz 'consultor-saida.log'
$erro = Join-Path $raiz 'consultor-erro.log'
if (Test-Path $saida) { Remove-Item $saida -Force }
if (Test-Path $erro) { Remove-Item $erro -Force }

$proc = Start-Process -FilePath $node `
    -ArgumentList @('consultor.js') `
    -WorkingDirectory $raiz `
    -RedirectStandardOutput $saida `
    -RedirectStandardError $erro `
    -WindowStyle Hidden `
    -PassThru

Write-Host "Consultor PID $($proc.Id) - logs: consultor-saida.log / consultor-erro.log"
