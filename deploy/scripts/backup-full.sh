#!/usr/bin/env bash
# =============================================================================================
# Backup completo do CompreGado, cada execução numa pasta /var/backups/compregado/AAAA-MM-DD_HHMMSS:
#   banco_compregado.sql.gz      dump do banco da API (usuários, anúncios, códigos)
#   banco_evolution.sql.gz       dump do banco da Evolution (instância e credenciais do WhatsApp)
#   whatsapp_instances.tar.gz    volume de sessões do WhatsApp (chaves de criptografia)
#   fotos_uploads.tar.gz         fotos dos anúncios
# Pastas com mais de RETENCAO_DIAS dias (padrão 7) são apagadas no fim.
#
# Rodar à mão (na VPS):
#   sudo /opt/compregado/deploy/scripts/backup-full.sh
#
# Agendar no cron do root (todo dia às 03:00):
#   sudo chmod +x /opt/compregado/deploy/scripts/backup-full.sh
#   sudo crontab -e
#   # adicione a linha abaixo, salve e saia:
#   0 3 * * * /opt/compregado/deploy/scripts/backup-full.sh >> /var/log/compregado-backup.log 2>&1
# Conferir:
#   sudo crontab -l
#   ls -lh /var/backups/compregado/
#   tail -n 30 /var/log/compregado-backup.log
#
# Os arquivos contêm a sessão do WhatsApp do bot e dados pessoais: ficam só para o root (umask 077).
# Um backup só na própria VPS não protege contra perda da VPS: copie para fora de vez em quando, ex.:
#   scp -r root@IP-DA-VPS:/var/backups/compregado/AAAA-MM-DD_HHMMSS .
# =============================================================================================
set -Eeuo pipefail

DIR_DEPLOY="$(cd "$(dirname "$0")/.." && pwd)"
DESTINO="${BACKUP_DIR:-/var/backups/compregado}"
RETENCAO_DIAS="${RETENCAO_DIAS:-7}"
PASTA="$DESTINO/$(date +%Y-%m-%d_%H%M%S)"

log() { echo "[$(date '+%F %T')] $*"; }

umask 077
mkdir -p "$DESTINO"

# Impede duas execuções simultâneas (ex.: cron + execução manual).
exec 9>"$DESTINO/.lock"
if ! flock -n 9; then
  log "Outro backup já está em andamento. Saindo."
  exit 1
fi

# Em caso de erro, apaga a pasta incompleta: um backup pela metade não pode parecer válido.
trap 'log "ERRO na linha $LINENO. Backup descartado: $PASTA"; rm -rf "$PASTA"' ERR

cd "$DIR_DEPLOY"
ler() { sed -n "s/^$1=//p" .env | tail -n 1 | tr -d '\r'; }
PG_USER="$(ler POSTGRES_USER)"
PG_USER="${PG_USER:-compregado}"
PG_DB="$(ler POSTGRES_DB)"
PG_DB="${PG_DB:-compregado}"
STACK="$(ler STACK_NOME)"
STACK="${STACK:-compregado}"

mkdir -p "$PASTA"
log "Início do backup em $PASTA"

# a) Bancos: pg_dump dentro do container do Postgres (docker compose exec = docker exec no serviço).
#    --clean --if-exists deixa o arquivo pronto para restaurar por cima de um banco existente.
for banco in "$PG_DB" evolution; do
  log "Dump do banco '$banco'..."
  docker compose exec -T postgres \
    pg_dump -U "$PG_USER" -d "$banco" --clean --if-exists \
    | gzip -9 > "$PASTA/banco_${banco}.sql.gz"
done

# b) Volumes: lidos por um container descartável, em modo somente leitura.
copiar_volume() {
  local volume="$1" arquivo="$2"
  log "Compactando o volume '$volume'..."
  docker run --rm \
    -v "$volume":/origem:ro \
    -v "$PASTA":/destino \
    alpine:3.20 tar -czf "/destino/$arquivo" -C /origem .
}
copiar_volume "${STACK}_evolution_instances" whatsapp_instances.tar.gz
copiar_volume "${STACK}_uploads" fotos_uploads.tar.gz

# O container auxiliar não herda o umask: restringe os arquivos dele também.
chmod 600 "$PASTA"/*

# Confere se cada arquivo compactado abre sem erro.
for arquivo in "$PASTA"/*.gz; do
  gzip -t "$arquivo"
done
log "Arquivos gerados:"
ls -lh "$PASTA"

# Retenção: apaga backups com mais de RETENCAO_DIAS dias.
find "$DESTINO" -mindepth 1 -maxdepth 1 -type d -name '20*' -mmin +$((RETENCAO_DIAS * 24 * 60)) \
  -print -exec rm -rf {} +

log "Backup concluído."
