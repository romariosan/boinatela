#!/usr/bin/env bash
# Deploy CompreGado: jar → systemd + limpa anúncios (mantém usuarios).
# Uso: ./deploy.sh
# Requer: SSH root@72.60.44.212 (chave ou CG_VPS_PASSWORD), Maven, JDK 17.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
HOST="${CG_VPS_HOST:-root@72.60.44.212}"
JAR_LOCAL="$ROOT/backend/target/backend-0.0.1-SNAPSHOT.jar"
JAR_REMOTE="/var/www/compregado-api/app.jar"
WWW_REMOTE="/var/www/appcompregado.com.br/html"
PW_FILE="${CG_VPS_PW_FILE:-}"

echo "==> Build backend"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
(cd "$ROOT/backend" && mvn -q -DskipTests package)

if [[ ! -f "$JAR_LOCAL" ]]; then
  echo "Jar não encontrado: $JAR_LOCAL" >&2
  exit 1
fi

SSH=(ssh -o StrictHostKeyChecking=accept-new)
SCP=(scp -o StrictHostKeyChecking=accept-new)
if [[ -n "${CG_VPS_PASSWORD:-}" ]]; then
  if command -v sshpass >/dev/null 2>&1; then
    SSH=(sshpass -e ssh -o StrictHostKeyChecking=accept-new)
    SCP=(sshpass -e scp -o StrictHostKeyChecking=accept-new)
    export SSHPASS="$CG_VPS_PASSWORD"
  fi
elif [[ -n "$PW_FILE" && -f "$PW_FILE" ]]; then
  if command -v sshpass >/dev/null 2>&1; then
    export SSHPASS
    SSHPASS="$(cat "$PW_FILE")"
    SSH=(sshpass -e ssh -o StrictHostKeyChecking=accept-new)
    SCP=(sshpass -e scp -o StrictHostKeyChecking=accept-new)
  fi
fi

STAMP="$(date +%Y%m%d-%H%M%S)"
echo "==> Backup + upload jar"
"${SSH[@]}" "$HOST" "cp -a '$JAR_REMOTE' '${JAR_REMOTE}.bak-deploy-${STAMP}'"
"${SCP[@]}" "$JAR_LOCAL" "$HOST:$JAR_REMOTE"
"${SSH[@]}" "$HOST" "systemctl restart compregado-api && systemctl is-active compregado-api"

echo "==> Truncate anúncios (mantém usuarios)"
"${SSH[@]}" "$HOST" 'docker exec -i boinatela-postgres-1 sh -c "psql -U \"\$POSTGRES_USER\" -d compregado_db -v ON_ERROR_STOP=1 -c \"TRUNCATE anuncio CASCADE;\""'

if [[ "${DEPLOY_WWW:-0}" == "1" ]]; then
  echo "==> Deploy www"
  (cd "$ROOT/mobile" && npm run build -- --configuration=production)
  TAR="/tmp/compregado-www-${STAMP}.tgz"
  tar -C "$ROOT/mobile/www" -czf "$TAR" .
  "${SCP[@]}" "$TAR" "$HOST:/tmp/compregado-www.tgz"
  "${SSH[@]}" "$HOST" "rm -rf '$WWW_REMOTE'/* && tar -xzf /tmp/compregado-www.tgz -C '$WWW_REMOTE' && rm -f /tmp/compregado-www.tgz"
fi

echo "==> Smoke"
"${SSH[@]}" "$HOST" 'curl -s -o /dev/null -w "%{http_code}" https://127.0.0.1/api/anuncios -k || curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/api/anuncios'
echo
echo "Deploy OK. Vitrine deve estar []. Admin usuario permanece."
