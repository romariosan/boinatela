-- Rodar com a API parada:
--   docker exec -i compregado-postgres-dev psql -U compregado -d compregado -v ON_ERROR_STOP=1 < backend/db/2026-10-16-curadoria-facebook.sql
BEGIN;

ALTER TABLE anuncio ADD COLUMN IF NOT EXISTS origem varchar(20) NOT NULL DEFAULT 'WHATSAPP';

COMMIT;
