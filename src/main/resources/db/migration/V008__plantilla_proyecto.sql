CREATE TABLE plantilla_proyecto (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id  BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    nombre      TEXT NOT NULL,
    descripcion TEXT,
    snapshot    JSONB NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_plantilla_proyecto_usuario ON plantilla_proyecto(usuario_id);
