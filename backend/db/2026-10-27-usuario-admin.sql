-- Flag de administrador para o painel /api/admin (moderação + prospecção).
BEGIN;

ALTER TABLE usuario ADD COLUMN IF NOT EXISTS admin boolean NOT NULL DEFAULT false;

COMMIT;
