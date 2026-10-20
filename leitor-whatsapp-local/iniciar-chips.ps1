$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $raiz

if (-not (Test-Path (Join-Path $raiz '.env'))) {
    Write-Host 'Falta o arquivo .env. Copie .env.example e preencha os dois tokens.'
    exit 1
}

foreach ($chip in @('sistema', 'radar', 'consultor')) {
    Start-Process powershell -WorkingDirectory $raiz -ArgumentList @(
        '-NoExit',
        '-Command',
        "`$Host.UI.RawUI.WindowTitle = 'CHIP $($chip.ToUpper())'; node $chip.js"
    )
}
