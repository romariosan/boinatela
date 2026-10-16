CREATE TABLE IF NOT EXISTS lead_contato (
    id BIGSERIAL PRIMARY KEY,
    anuncio_id BIGINT NOT NULL REFERENCES anuncio (id) ON DELETE CASCADE,
    whatsapp VARCHAR(20) NOT NULL,
    data_criacao TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_lead_contato_anuncio ON lead_contato (anuncio_id);
