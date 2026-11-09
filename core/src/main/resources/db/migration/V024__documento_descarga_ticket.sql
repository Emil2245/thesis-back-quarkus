-- Core stores only a SHA-256 digest of each one-use download capability.
-- Object expiry is derived from V023.uploaded_at; ticket expiry never extends it.
CREATE TABLE documento_descarga_ticket (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    exportacion_id BIGINT NOT NULL REFERENCES documento_exportacion(id) ON DELETE CASCADE,
    token_sha256   CHAR(64) NOT NULL UNIQUE CHECK (token_sha256 ~ '^[0-9a-f]{64}$'),
    issued_at      TIMESTAMPTZ NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL CHECK (expires_at > issued_at),
    consumed_at    TIMESTAMPTZ
);
CREATE INDEX idx_documento_descarga_ticket_expiry ON documento_descarga_ticket(expires_at);
