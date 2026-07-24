-- V001__baseline.sql
-- Baseline schema for the SERCOP propuestas-técnico-económicas platform.
-- Source: thesis-docs/plan/architecture/06-database-schema.md (doc commit d7508eb).
-- Conventions: snake_case, singular; VARCHAR+CHECK for enums; BIGINT IDENTITY PKs;
-- TIMESTAMPTZ timestamps; NUMERIC(14,6) money, NUMERIC(5,4) percentages.

-- =========================================================================
-- 2.1  usuario
-- =========================================================================
CREATE TABLE usuario (
  id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre           VARCHAR(200) NOT NULL,
  email            VARCHAR(320) NOT NULL UNIQUE,
  password_hash    VARCHAR(72)  NOT NULL,
  rol              VARCHAR(12)  NOT NULL DEFAULT 'USUARIO'
                   CHECK (rol IN ('USUARIO','SUPER_ADMIN')),
  email_verificado BOOLEAN      NOT NULL DEFAULT FALSE,
  activo           BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- =========================================================================
-- 2.2  refresh_token, token_usuario
-- =========================================================================
CREATE TABLE refresh_token (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  usuario_id  BIGINT      NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  token_hash  VARCHAR(64) NOT NULL UNIQUE,
  expira_en   TIMESTAMPTZ NOT NULL,
  revocado_en TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE token_usuario (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  usuario_id    BIGINT      NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  tipo          VARCHAR(20) NOT NULL CHECK (tipo IN
                ('VERIFICACION_EMAIL','RESET_PASSWORD','INVITACION','CAMBIO_EMAIL')),
  token_hash    VARCHAR(64) NOT NULL UNIQUE,
  email_destino VARCHAR(320),
  expira_en     TIMESTAMPTZ NOT NULL,
  usado_en      TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =========================================================================
-- 2.3  proyecto
-- =========================================================================
CREATE TABLE proyecto (
  id                         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  usuario_id                 BIGINT       NOT NULL REFERENCES usuario(id) ON DELETE RESTRICT,
  nombre_proyecto            TEXT         NOT NULL,
  codigo                     VARCHAR(50),
  descripcion                TEXT,
  anio                       SMALLINT     NOT NULL,
  fecha_inicio               DATE,
  plazo_ejecucion            SMALLINT     CHECK (plazo_ejecucion > 0),
  plazo_unidad               VARCHAR(10)  CHECK (plazo_unidad IN ('SEMANA','MES')),
  estado                     VARCHAR(12)  NOT NULL DEFAULT 'BORRADOR'
                             CHECK (estado IN ('BORRADOR','EN_PROCESO','FINALIZADO')),
  direccion_institucional    VARCHAR(200) NOT NULL,
  subdireccion_institucional VARCHAR(200),
  logo                       BYTEA,
  created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- =========================================================================
-- 2.4  firmante
-- =========================================================================
CREATE TABLE firmante (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  proyecto_id BIGINT       NOT NULL REFERENCES proyecto(id) ON DELETE CASCADE,
  nombre      VARCHAR(200) NOT NULL,
  cargo       VARCHAR(300) NOT NULL,
  rol         VARCHAR(12)  NOT NULL CHECK (rol IN ('CONSOLIDADO','APROBADO')),
  orden       SMALLINT     NOT NULL,
  UNIQUE (proyecto_id, rol, orden)
);

-- =========================================================================
-- 2.5  parametros_sistema, parametros_proyecto
-- =========================================================================
CREATE TABLE parametros_sistema (
  id                             SMALLINT PRIMARY KEY CHECK (id = 1),
  porcentaje_herramienta_menor   NUMERIC(5,4) NOT NULL DEFAULT 0.0500
                                 CHECK (porcentaje_herramienta_menor BETWEEN 0 AND 0.2000),
  porcentaje_indirecto           NUMERIC(5,4)
                                 CHECK (porcentaje_indirecto BETWEEN 0 AND 1.0000),
  iva                            NUMERIC(5,4) NOT NULL DEFAULT 0.1500
                                 CHECK (iva BETWEEN 0 AND 0.3000),
  moneda                         VARCHAR(10)  NOT NULL DEFAULT 'USD',
  mostrar_secciones_vacias       BOOLEAN      NOT NULL DEFAULT TRUE,
  sufijos_seccion_activos        BOOLEAN      NOT NULL DEFAULT TRUE,
  mostrar_subtotales_seccion     BOOLEAN      NOT NULL DEFAULT TRUE,
  mostrar_subtotales_pie         BOOLEAN      NOT NULL DEFAULT FALSE,
  mostrar_nombre_proyecto_header BOOLEAN      NOT NULL DEFAULT FALSE,
  enumerar_apus                  BOOLEAN      NOT NULL DEFAULT FALSE,
  mensaje_footer                 TEXT         NOT NULL DEFAULT 'Este precio no incluye IVA',
  modo_codigo_rubro              VARCHAR(12)  NOT NULL DEFAULT 'AUTOGENERADO'
                                 CHECK (modo_codigo_rubro IN ('AUTOGENERADO','MANUAL')),
  updated_at                     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE parametros_proyecto (
  proyecto_id                    BIGINT PRIMARY KEY REFERENCES proyecto(id) ON DELETE CASCADE,
  porcentaje_herramienta_menor   NUMERIC(5,4) NOT NULL DEFAULT 0.0500
                                 CHECK (porcentaje_herramienta_menor BETWEEN 0 AND 0.2000),
  porcentaje_indirecto           NUMERIC(5,4)
                                 CHECK (porcentaje_indirecto BETWEEN 0 AND 1.0000),
  iva                            NUMERIC(5,4) NOT NULL DEFAULT 0.1500
                                 CHECK (iva BETWEEN 0 AND 0.3000),
  moneda                         VARCHAR(10)  NOT NULL DEFAULT 'USD',
  mostrar_secciones_vacias       BOOLEAN      NOT NULL DEFAULT TRUE,
  sufijos_seccion_activos        BOOLEAN      NOT NULL DEFAULT TRUE,
  mostrar_subtotales_seccion     BOOLEAN      NOT NULL DEFAULT TRUE,
  mostrar_subtotales_pie         BOOLEAN      NOT NULL DEFAULT FALSE,
  mostrar_nombre_proyecto_header BOOLEAN      NOT NULL DEFAULT FALSE,
  enumerar_apus                  BOOLEAN      NOT NULL DEFAULT FALSE,
  mensaje_footer                 TEXT         NOT NULL DEFAULT 'Este precio no incluye IVA',
  modo_codigo_rubro              VARCHAR(12)  NOT NULL DEFAULT 'AUTOGENERADO'
                                 CHECK (modo_codigo_rubro IN ('AUTOGENERADO','MANUAL')),
  updated_at                     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- =========================================================================
-- 2.6  base_insumos, insumo
-- =========================================================================
CREATE TABLE base_insumos (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre      VARCHAR(200) NOT NULL,
  tipo        VARCHAR(10)  NOT NULL CHECK (tipo IN ('CENTRAL','PROYECTO')),
  proyecto_id BIGINT       REFERENCES proyecto(id) ON DELETE CASCADE,
  archivada   BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CHECK ((tipo = 'CENTRAL' AND proyecto_id IS NULL)
      OR (tipo = 'PROYECTO' AND proyecto_id IS NOT NULL))
);

CREATE TABLE insumo (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  base_id         BIGINT        NOT NULL REFERENCES base_insumos(id) ON DELETE CASCADE,
  codigo          VARCHAR(50)   NOT NULL,
  tipo            VARCHAR(12)   NOT NULL
                  CHECK (tipo IN ('EQUIPO','MANO_OBRA','MATERIAL','TRANSPORTE')),
  descripcion     TEXT          NOT NULL,
  unidad          VARCHAR(10)   NOT NULL,
  precio_unitario NUMERIC(14,6) NOT NULL CHECK (precio_unitario > 0),
  created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
  UNIQUE (base_id, codigo),
  CHECK (tipo NOT IN ('EQUIPO','MANO_OBRA') OR unidad = 'h')
);

-- =========================================================================
-- 2.7  unidad_catalogo
-- =========================================================================
CREATE TABLE unidad_catalogo (
  codigo      VARCHAR(10)  PRIMARY KEY,
  descripcion VARCHAR(100) NOT NULL
);

-- =========================================================================
-- 2.8  presupuesto
-- =========================================================================
CREATE TABLE presupuesto (
  id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  proyecto_id          BIGINT        NOT NULL REFERENCES proyecto(id) ON DELETE CASCADE,
  version              SMALLINT      NOT NULL,
  es_vigente           BOOLEAN       NOT NULL DEFAULT FALSE,
  origen_id            BIGINT        REFERENCES presupuesto(id) ON DELETE SET NULL,
  notas                TEXT,
  porcentaje_indirecto NUMERIC(5,4)  CHECK (porcentaje_indirecto BETWEEN 0 AND 1.0000),
  total                NUMERIC(14,6) NOT NULL DEFAULT 0,
  created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  UNIQUE (proyecto_id, version)
);
CREATE UNIQUE INDEX ux_presupuesto_vigente ON presupuesto (proyecto_id) WHERE es_vigente;

-- =========================================================================
-- 2.9  capitulo
-- =========================================================================
CREATE TABLE capitulo (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  presupuesto_id BIGINT        NOT NULL REFERENCES presupuesto(id) ON DELETE CASCADE,
  parent_id      BIGINT        REFERENCES capitulo(id) ON DELETE CASCADE,
  item           VARCHAR(20)   NOT NULL,
  descripcion    TEXT          NOT NULL,
  orden          SMALLINT      NOT NULL,
  total          NUMERIC(14,6) NOT NULL DEFAULT 0,
  UNIQUE (presupuesto_id, item)
);

-- =========================================================================
-- 2.10  apu, apu_seccion, apu_detalle
-- =========================================================================
CREATE TABLE apu (
  id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  presupuesto_id       BIGINT        NOT NULL REFERENCES presupuesto(id) ON DELETE CASCADE,
  codigo               VARCHAR(20)   NOT NULL,
  descripcion          TEXT          NOT NULL,
  unidad               VARCHAR(10)   NOT NULL,
  es_auxiliar          BOOLEAN       NOT NULL DEFAULT FALSE,
  porcentaje_indirecto NUMERIC(5,4)  CHECK (porcentaje_indirecto BETWEEN 0 AND 1.0000),
  porcentaje_descuento NUMERIC(5,4)  NOT NULL DEFAULT 0
                       CHECK (porcentaje_descuento BETWEEN 0 AND 0.5000),
  costo_directo        NUMERIC(14,6) NOT NULL DEFAULT 0,
  costo_indirecto      NUMERIC(14,6) NOT NULL DEFAULT 0,
  costo_total          NUMERIC(14,6) NOT NULL DEFAULT 0,
  created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
  UNIQUE (presupuesto_id, codigo)
);

CREATE TABLE apu_seccion (
  id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  apu_id   BIGINT        NOT NULL REFERENCES apu(id) ON DELETE CASCADE,
  tipo     VARCHAR(12)   NOT NULL
           CHECK (tipo IN ('EQUIPO','MANO_OBRA','MATERIAL','TRANSPORTE')),
  subtotal NUMERIC(14,6) NOT NULL DEFAULT 0,
  orden    SMALLINT      NOT NULL,
  UNIQUE (apu_id, tipo)
);

CREATE TABLE apu_detalle (
  id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  seccion_id             BIGINT        NOT NULL REFERENCES apu_seccion(id) ON DELETE CASCADE,
  insumo_id              BIGINT        REFERENCES insumo(id) ON DELETE RESTRICT,
  apu_auxiliar_id        BIGINT        REFERENCES apu(id) ON DELETE RESTRICT,
  descripcion            TEXT          NOT NULL,
  orden                  SMALLINT      NOT NULL,
  es_herramienta_menor   BOOLEAN       NOT NULL DEFAULT FALSE,
  cantidad               NUMERIC(12,6) CHECK (cantidad > 0),
  tarifa_jornal          NUMERIC(14,6) CHECK (tarifa_jornal > 0),
  costo_hora             NUMERIC(14,6) NOT NULL DEFAULT 0,
  rendimiento            NUMERIC(10,6) CHECK (rendimiento > 0),
  unidad                 VARCHAR(10),
  precio_unitario_tarifa NUMERIC(14,6) CHECK (precio_unitario_tarifa > 0),
  costo                  NUMERIC(14,6) NOT NULL DEFAULT 0,
  CHECK (NOT (insumo_id IS NOT NULL AND apu_auxiliar_id IS NOT NULL)),
  CHECK (NOT es_herramienta_menor OR (insumo_id IS NULL AND apu_auxiliar_id IS NULL
         AND tarifa_jornal IS NULL AND rendimiento IS NULL AND cantidad IS NULL))
);

-- =========================================================================
-- 2.11  rubro
-- =========================================================================
CREATE TABLE rubro (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  capitulo_id     BIGINT        NOT NULL REFERENCES capitulo(id) ON DELETE CASCADE,
  apu_id          BIGINT        NOT NULL UNIQUE REFERENCES apu(id) ON DELETE RESTRICT,
  item            VARCHAR(20)   NOT NULL,
  codigo          VARCHAR(20)   NOT NULL,
  descripcion     TEXT          NOT NULL,
  unidad          VARCHAR(10)   NOT NULL,
  cantidad        NUMERIC(12,6) NOT NULL CHECK (cantidad > 0),
  precio_unitario NUMERIC(14,6) NOT NULL DEFAULT 0,
  precio_total    NUMERIC(14,6) NOT NULL DEFAULT 0
);

-- =========================================================================
-- 2.12  plantilla_apu
-- =========================================================================
CREATE TABLE plantilla_apu (
  id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre             TEXT        NOT NULL,
  tipo               VARCHAR(10) NOT NULL CHECK (tipo IN ('SISTEMA','PERSONAL')),
  usuario_id         BIGINT      REFERENCES usuario(id) ON DELETE CASCADE,
  descripcion_rubro  TEXT,
  unidad             VARCHAR(10),
  snapshot_secciones JSONB       NOT NULL,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((tipo = 'SISTEMA' AND usuario_id IS NULL)
      OR (tipo = 'PERSONAL' AND usuario_id IS NOT NULL))
);

-- =========================================================================
-- 2.13  cronograma, actividad
-- =========================================================================
CREATE TABLE cronograma (
  id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  presupuesto_id         BIGINT        NOT NULL UNIQUE REFERENCES presupuesto(id) ON DELETE CASCADE,
  unidad_tiempo          VARCHAR(10)   NOT NULL CHECK (unidad_tiempo IN ('SEMANA','MES')),
  numero_periodos        SMALLINT      NOT NULL CHECK (numero_periodos > 0),
  total_general_revisado NUMERIC(14,6),
  fecha_revision         TIMESTAMPTZ,
  updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE actividad (
  id                 BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  cronograma_id      BIGINT        NOT NULL REFERENCES cronograma(id) ON DELETE CASCADE,
  rubro_id           BIGINT        NOT NULL UNIQUE REFERENCES rubro(id) ON DELETE CASCADE,
  peso_ponderado     NUMERIC(7,4)  NOT NULL DEFAULT 0,
  avance_por_periodo JSONB         NOT NULL DEFAULT '{}'
);

-- =========================================================================
-- 2.14  valor_referencia
-- =========================================================================
CREATE TABLE valor_referencia (
  clave       VARCHAR(50)  PRIMARY KEY,
  valor       VARCHAR(100) NOT NULL,
  descripcion TEXT         NOT NULL,
  fuente      VARCHAR(200) NOT NULL,
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- =========================================================================
-- 2.15  log_actividad
-- =========================================================================
CREATE TABLE log_actividad (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  usuario_id BIGINT      REFERENCES usuario(id) ON DELETE SET NULL,
  evento     VARCHAR(60) NOT NULL,
  entidad    VARCHAR(30),
  entidad_id BIGINT,
  detalle    JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =========================================================================
-- §5  Indexes
-- =========================================================================
CREATE INDEX ix_proyecto_usuario         ON proyecto(usuario_id);
CREATE INDEX ix_presupuesto_proyecto     ON presupuesto(proyecto_id);
CREATE INDEX ix_capitulo_presupuesto     ON capitulo(presupuesto_id);
CREATE INDEX ix_capitulo_parent          ON capitulo(parent_id);
CREATE INDEX ix_rubro_capitulo           ON rubro(capitulo_id);
CREATE INDEX ix_apu_presupuesto          ON apu(presupuesto_id);
CREATE INDEX ix_apu_seccion_apu          ON apu_seccion(apu_id);
CREATE INDEX ix_apu_detalle_seccion      ON apu_detalle(seccion_id);
CREATE INDEX ix_apu_detalle_insumo       ON apu_detalle(insumo_id);
CREATE INDEX ix_apu_detalle_auxiliar    ON apu_detalle(apu_auxiliar_id);
CREATE INDEX ix_insumo_base              ON insumo(base_id);
CREATE INDEX ix_base_insumos_proyecto    ON base_insumos(proyecto_id);
CREATE INDEX ix_firmante_proyecto        ON firmante(proyecto_id);
CREATE INDEX ix_actividad_cronograma     ON actividad(cronograma_id);
CREATE INDEX ix_plantilla_usuario        ON plantilla_apu(usuario_id);
CREATE INDEX ix_log_fecha                ON log_actividad(created_at DESC);
CREATE INDEX ix_log_usuario              ON log_actividad(usuario_id);
CREATE INDEX ix_refresh_usuario          ON refresh_token(usuario_id);
CREATE INDEX ix_token_usuario            ON token_usuario(usuario_id);
