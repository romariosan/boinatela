-- Rodar com a API parada:
--   docker exec -i compregado-postgres-dev psql -U compregado -d compregado -v ON_ERROR_STOP=1 < backend/db/2026-10-13-racas-status-cidade-uf.sql
-- O ddl-auto=update do Hibernate não recria os CHECKs de enum nem converte dados existentes.
BEGIN;

UPDATE anuncio SET raca = 'OUTRA' WHERE raca = 'OUTRO';
ALTER TABLE anuncio DROP CONSTRAINT IF EXISTS anuncio_raca_check;
ALTER TABLE anuncio ADD CONSTRAINT anuncio_raca_check CHECK (raca IN (
    'NELORE', 'ANGUS', 'GIROLANDO', 'BRAHMAN', 'SENEPOL', 'BRANGUS', 'GUZERA',
    'TABAPUA', 'GIR', 'HOLANDES', 'CARACU', 'CANCHIM', 'MISTO', 'OUTRA'));

ALTER TABLE anuncio ADD COLUMN IF NOT EXISTS status varchar(20) NOT NULL DEFAULT 'ATIVO';
ALTER TABLE anuncio DROP CONSTRAINT IF EXISTS anuncio_status_check;
ALTER TABLE anuncio ADD CONSTRAINT anuncio_status_check CHECK (status IN ('ATIVO', 'VENDIDO'));

-- Cidades gravadas antes do padrão "Cidade - UF" são todas da região de Formosa/GO.
UPDATE anuncio SET cidade = initcap(trim(cidade)) || ' - GO' WHERE cidade !~ ' - [A-Z]{2}$';

COMMIT;
