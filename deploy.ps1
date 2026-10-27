# Deploy CompreGado na Hostinger (Windows / PuTTY).
# Uso: .\deploy.ps1
# Senha: $env:CG_VPS_PASSWORD ou arquivo em $env:CG_VPS_PW_FILE (nunca imprime).
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$HostName = if ($env:CG_VPS_HOST) { $env:CG_VPS_HOST } else { "root@72.60.44.212" }
$JarLocal = Join-Path $Root "backend\target\backend-0.0.1-SNAPSHOT.jar"
$JarRemote = "/var/www/compregado-api/app.jar"
$WwwRemote = "/var/www/appcompregado.com.br/html"
$Plink = "C:\Program Files\PuTTY\plink.exe"
$Pscp = "C:\Program Files\PuTTY\pscp.exe"
$JavaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Java\jdk-17.0.1" }

function Get-Pw {
  if ($env:CG_VPS_PASSWORD) { return $env:CG_VPS_PASSWORD }
  if ($env:CG_VPS_PW_FILE -and (Test-Path $env:CG_VPS_PW_FILE)) {
    return (Get-Content -Raw $env:CG_VPS_PW_FILE).Trim()
  }
  throw "Defina CG_VPS_PASSWORD ou CG_VPS_PW_FILE"
}

$pw = Get-Pw
$env:JAVA_HOME = $JavaHome

Write-Host "==> Build backend"
Set-Location (Join-Path $Root "backend")
& mvn -q "-DskipTests" package
if (-not (Test-Path $JarLocal)) { throw "Jar ausente: $JarLocal" }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$pwFile = Join-Path $env:TEMP "cg-deploy-$stamp.txt"
Set-Content -Path $pwFile -Value $pw -NoNewline

try {
  Write-Host "==> Backup + upload jar"
  & $Plink -batch -pwfile $pwFile $HostName "cp -a $JarRemote ${JarRemote}.bak-deploy-$stamp"
  & $Pscp -batch -pwfile $pwFile $JarLocal "${HostName}:$JarRemote"
  & $Plink -batch -pwfile $pwFile $HostName "systemctl restart compregado-api && systemctl is-active compregado-api"

  Write-Host "==> Truncate anuncio CASCADE (mantem usuarios)"
  $sql = 'docker exec -i boinatela-postgres-1 sh -c ''psql -U "$POSTGRES_USER" -d compregado_db -c "TRUNCATE anuncio CASCADE;"'''
  & $Plink -batch -pwfile $pwFile $HostName $sql

  if ($env:DEPLOY_WWW -eq "1") {
    Write-Host "==> Build + deploy www"
    Set-Location (Join-Path $Root "mobile")
    & npm run build -- --configuration=production
    $tar = Join-Path $env:TEMP "compregado-www-$stamp.tgz"
    if (Get-Command tar -ErrorAction SilentlyContinue) {
      & tar -C (Join-Path $Root "mobile\www") -czf $tar .
      & $Pscp -batch -pwfile $pwFile $tar "${HostName}:/tmp/compregado-www.tgz"
      & $Plink -batch -pwfile $pwFile $HostName "rm -rf $WwwRemote/* && tar -xzf /tmp/compregado-www.tgz -C $WwwRemote && rm -f /tmp/compregado-www.tgz"
    }
  }

  Write-Host "==> Contagem anuncio"
  & $Plink -batch -pwfile $pwFile $HostName 'docker exec -i boinatela-postgres-1 sh -c ''psql -U "$POSTGRES_USER" -d compregado_db -t -c "SELECT count(*) FROM anuncio;"'''
  Write-Host "Deploy OK."
}
finally {
  Remove-Item -Force $pwFile -ErrorAction SilentlyContinue
}
