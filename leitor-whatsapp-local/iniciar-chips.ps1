# Abre os três chips em janelas PowerShell. O título da janela NÃO é o executável.
$raiz = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $raiz

if (-not (Test-Path (Join-Path $raiz '.env'))) {
    Write-Host 'Falta o arquivo .env. Copie .env.example e preencha os dois tokens.'
    exit 1
}

$node = 'C:\Program Files\nodejs\node.exe'
if (-not (Test-Path $node)) {
    $node = (Get-Command node -ErrorAction Stop).Source
}

Remove-Item Env:PUPPETEER_CACHE_DIR -ErrorAction SilentlyContinue
Remove-Item Env:PUPPETEER_TMP_DIR -ErrorAction SilentlyContinue

foreach ($chip in @('sistema', 'radar', 'consultor')) {
    $titulo = 'CHIP ' + $chip.ToUpperInvariant()
    # -FilePath é sempre o powershell; o título vai só em WindowTitle via -Command.
    Start-Process -FilePath 'powershell.exe' -WorkingDirectory $raiz -ArgumentList @(
        '-NoExit',
        '-Command',
        "`$Host.UI.RawUI.WindowTitle = '$titulo'; & '$node' '$chip.js'"
    )
}
