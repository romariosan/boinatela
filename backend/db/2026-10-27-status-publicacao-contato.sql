-- Moderação da vitrine: status_publicacao + status_contato.
-- Rodar com a API parada (ddl-auto cria colunas, mas não aplica CHECK nem o backfill):
--   docker exec -i boinatela-postgres-1 sh -c 'psql -U "$POSTGRES_USER" -d compregado_db -v ON_ERROR_STOP=1' < backend/db/2026-10-27-status-publicacao-contato.sql
BEGIN;

ALTER TABLE anuncio ADD COLUMN IF NOT EXISTS status_publicacao varchar(20) NOT NULL DEFAULT 'PENDENTE';
ALTER TABLE anuncio ADD COLUMN IF NOT EXISTS status_contato varchar(20) NOT NULL DEFAULT 'NAO_CONTATADO';

ALTER TABLE anuncio DROP CONSTRAINT IF EXISTS anuncio_status_publicacao_check;
ALTER TABLE anuncio ADD CONSTRAINT anuncio_status_publicacao_check
    CHECK (status_publicacao IN ('PENDENTE', 'APROVADO', 'REJEITADO'));

ALTER TABLE anuncio DROP CONSTRAINT IF EXISTS anuncio_status_contato_check;
ALTER TABLE anuncio ADD CONSTRAINT anuncio_status_contato_check
    CHECK (status_contato IN ('NAO_CONTATADO', 'CONTATADO', 'REIVINDICADO'));

-- Já estavam na vitrine (ativos, publicados, com foto): continuam visíveis após o filtro APROVADO.
UPDATE anuncio
SET status_publicacao = 'APROVADO'
WHERE ativo = true
  AND status = 'ATIVO'
  AND status_processamento = 'PUBLICADO'
  AND url_foto_principal IS NOT NULL
  AND trim(url_foto_principal) <> '';

-- Rascunhos e demais ficam PENDENTE (default da coluna).

UPDATE anuncio
SET status_contato = 'REIVINDICADO'
WHERE reivindicado = true;

COMMIT;
