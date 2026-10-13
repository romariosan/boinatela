-- Validação reversa do WhatsApp. O ddl-auto: update também cria as colunas; o script serve para bancos
-- em que o Hibernate não roda com update. Contas existentes começam não verificadas.
BEGIN;

ALTER TABLE usuario ADD COLUMN IF NOT EXISTS telefone_verificado boolean NOT NULL DEFAULT false;
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS codigo_verificacao varchar(16);

COMMIT;
