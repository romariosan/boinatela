-- Rodar com a API parada:
--   docker exec -i compregado-postgres-dev psql -U compregado -d compregado -v ON_ERROR_STOP=1 < backend/db/2026-10-15-plano-pro-pausado.sql
-- O ddl-auto=update do Hibernate cria as colunas novas, mas não recria o CHECK do enum de status.
BEGIN;

ALTER TABLE anuncio DROP CONSTRAINT IF EXISTS anuncio_status_check;
ALTER TABLE anuncio ADD CONSTRAINT anuncio_status_check CHECK (status IN ('ATIVO', 'PAUSADO', 'VENDIDO'));

-- Anúncios anteriores ficam com data_criacao nula e contam como os mais antigos no rebaixamento do PRO.
ALTER TABLE anuncio ADD COLUMN IF NOT EXISTS data_criacao timestamp(6);
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS data_expiracao_premium date;

COMMIT;
