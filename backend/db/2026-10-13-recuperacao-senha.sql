-- "Esqueci minha senha". O ddl-auto: update também cria a tabela; o script serve para bancos
-- em que o Hibernate não roda com update.
BEGIN;

CREATE TABLE IF NOT EXISTS codigo_recuperacao (
    id             bigserial PRIMARY KEY,
    fk_usuario     bigint       NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    whatsapp       varchar(20)  NOT NULL,
    codigo_hash    varchar(100) NOT NULL,
    data_criacao   timestamp(6) NOT NULL,
    data_expiracao timestamp(6) NOT NULL,
    utilizado      boolean      NOT NULL DEFAULT false,
    tentativas     integer      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_codigo_recuperacao_usuario ON codigo_recuperacao (fk_usuario, data_criacao DESC);

COMMIT;
