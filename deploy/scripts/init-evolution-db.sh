#!/usr/bin/env sh
# Executado pelo serviço "postgres-init" a cada "docker compose up". Idempotente:
#   - cria o usuário "evolution" (ou atualiza a senha para a do .env, permitindo trocar a senha);
#   - cria o banco "evolution" com esse dono, se ainda não existir.
# A Evolution só enxerga o próprio banco; a API do CompreGado continua com o usuário principal.
set -eu

psql -v ON_ERROR_STOP=1 -v senha="$EVOLUTION_DB_PASSWORD" -v principal="$PGDATABASE" <<'SQL'
SELECT format('CREATE ROLE evolution LOGIN PASSWORD %L', :'senha')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'evolution') \gexec

SELECT format('ALTER ROLE evolution WITH LOGIN PASSWORD %L', :'senha') \gexec

SELECT 'CREATE DATABASE evolution OWNER evolution'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'evolution') \gexec

REVOKE ALL ON DATABASE evolution FROM PUBLIC;
SELECT format('REVOKE CONNECT ON DATABASE %I FROM PUBLIC', :'principal') \gexec
SQL

echo "Banco e usuário da Evolution prontos."
