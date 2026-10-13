#!/usr/bin/env sh
# Backup do banco e das fotos. Agende no cron da VPS, ex.: 0 3 * * * /opt/boinatela/deploy/backup.sh
set -eu

cd "$(dirname "$0")"
DESTINO="${BACKUP_DIR:-/opt/boinatela-backups}"
DATA="$(date +%Y-%m-%d_%H%M)"
mkdir -p "$DESTINO"

docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  | gzip > "$DESTINO/banco_$DATA.sql.gz"
docker compose exec -T api tar -czf - -C /data uploads > "$DESTINO/fotos_$DATA.tar.gz"

find "$DESTINO" -type f -mtime +14 -delete
echo "Backup salvo em $DESTINO ($DATA)"
