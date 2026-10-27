-- Fila de enriquecimento Gemini em segundo plano (captura já gravada).
-- Com ddl-auto=update a API cria as colunas sozinha; este script documenta o schema.
BEGIN;

ALTER TABLE anuncio ADD COLUMN IF NOT EXISTS gemini_pendente boolean NOT NULL DEFAULT false;
ALTER TABLE anuncio ADD COLUMN IF NOT EXISTS tentativas_gemini integer NOT NULL DEFAULT 0;
ALTER TABLE anuncio ADD COLUMN IF NOT EXISTS proxima_tentativa_gemini timestamp;

COMMIT;
