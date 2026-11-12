-- Core owns durable idempotency and lease fencing. S3 bytes and credentials never live here.
CREATE TABLE documento_exportacion (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    operation_id       UUID NOT NULL UNIQUE DEFAULT uuidv7(),
    usuario_id         BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    idempotency_key    UUID NOT NULL,
    presupuesto_id     BIGINT REFERENCES presupuesto(id) ON DELETE SET NULL,
    presupuesto_public_id UUID NOT NULL,
    documento          VARCHAR(28) NOT NULL CHECK (documento IN ('presupuesto', 'apus', 'especificaciones-tecnicas')),
    request_sha256     CHAR(64) NOT NULL CHECK (request_sha256 ~ '^[0-9a-f]{64}$'),
    revision_sha256    CHAR(64) NOT NULL CHECK (revision_sha256 ~ '^[0-9a-f]{64}$'),
    estado             VARCHAR(10) NOT NULL DEFAULT 'RESERVADA'
                       CHECK (estado IN ('RESERVADA', 'SUBIDA', 'AUDITADA')),
    fence              BIGINT NOT NULL DEFAULT 1 CHECK (fence > 0),
    lease_until        TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '2 minutes'),
    objeto_sha256      VARCHAR(44),
    objeto_bytes       BIGINT CHECK (objeto_bytes > 0 AND objeto_bytes <= 67108864),
    objeto_mime        VARCHAR(100),
    objeto_nombre      VARCHAR(128),
    uploaded_at        TIMESTAMPTZ,
    auditado_en        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (usuario_id, idempotency_key),
    CHECK ((estado = 'RESERVADA' AND uploaded_at IS NULL AND objeto_sha256 IS NULL
            AND objeto_bytes IS NULL AND objeto_mime IS NULL AND objeto_nombre IS NULL)
        OR (estado IN ('SUBIDA', 'AUDITADA') AND uploaded_at IS NOT NULL AND objeto_sha256 IS NOT NULL
            AND objeto_bytes IS NOT NULL AND objeto_mime IS NOT NULL AND objeto_nombre IS NOT NULL)),
    CHECK (estado <> 'AUDITADA' OR auditado_en IS NOT NULL)
);
CREATE INDEX idx_documento_exportacion_lease ON documento_exportacion (estado, lease_until);
CREATE INDEX idx_documento_exportacion_uploaded ON documento_exportacion (uploaded_at) WHERE uploaded_at IS NOT NULL;
