# 003 — Postgres schema baseline (V001__baseline.sql + V002__seed.sql + V003__seed_insumos.sql)

- **Status:** TODO
- **Iteration:** I-01 (roadmap/01, weeks 1–2)
- **Depends on:** 001 (Quarkus skeleton with Flyway wired), 002 (CI catches migration regressions)
- **Blocks:** 004 (auth needs `usuario`, `refresh_token`, `token_usuario`), all feature plans that touch the DB

---

## Context

Plan 001 shipped a placeholder `V001__baseline.sql` (`SELECT 1;`). This plan
replaces that placeholder with the real schema: 19 tables in dependency
order plus indexes, plus two seed migrations for reference catalogs and
the IESS central input base.

The canonical spec is `../../thesis-docs/plan/architecture/06-database-schema.md`.
Every table, column, type, constraint, and index in this plan is copied
from that file — do not invent, do not shorten, do not "improve" types
(e.g. don't swap `BIGINT IDENTITY` for `UUID` because it "feels safer";
the doc explicitly picks IDENTITY for cost).

**Numeric precision is load-bearing** for the thesis's `exactitud_calculo`
variable (0% deviation vs certified cases). All monetary columns are
`NUMERIC(14,6)`; percentages `NUMERIC(5,4)`; quantities `NUMERIC(12,6)`;
rendimiento `NUMERIC(10,6)`. Rounding to 2 dp happens **only at
export** — the DB stores 6 decimals throughout.

**Write-through totals** (`apu_detalle.costo`, `apu_seccion.subtotal`,
`apu.costo_*`, `rubro.precio_total`, `capitulo.total`, `presupuesto.total`,
`actividad.peso_ponderado`) are **columns in the schema, not views**. The
motor (plan 005) recomputes them on each mutation, in the same transaction.
This plan just declares the columns with `DEFAULT 0`; nothing computes them
yet.

**No entities in this plan.** No `@Entity` classes. This plan is pure SQL.
Hibernate is configured (plan 001) as `generation: validate` so it will
reject startup if a mapped entity disagrees with the DB — but there are
zero mapped entities right now. Feature plans (004+) add entities against
the tables this plan creates.

## In scope

- `src/main/resources/db/migration/V001__baseline.sql` — **overwrite** the
  `SELECT 1;` stub with the full baseline schema (§2 below).
- `src/main/resources/db/migration/V002__seed.sql` — new. Seed
  `unidad_catalogo`, `parametros_sistema`, `valor_referencia` (Anexo A
  minus CAMICON, per source doc §5).
- `src/main/resources/db/migration/V003__seed_insumos.sql` — new. One
  `base_insumos` CENTRAL row + 93 IESS `insumo` rows loaded from
  `../../thesis-docs/plan/domain/_artifacts/insumos-seed-apus-cetro-medico-tulcan.csv`.

## Out of scope — do NOT touch or add

- Any `@Entity` class. Entities land in later plans as they're needed.
- Any Panache `PanacheEntity` extends. Same reason.
- Repository/service/resource code. This plan is DDL + seed only.
- The `unidad_catalogo` seed for non-standard units. Ship the 11 canonical
  units listed in `06-database-schema.md §5`; anything else is a later
  concern.
- The CAMICON `valor_referencia` values. Source doc §5 explicitly excludes
  them pending license confirmation (agenda A7 — a future interview).
- A super-admin user seed. Not specified by source doc §5; will be
  handled by the auth plan (004) or a manual bootstrap step.

## Repo conventions to match

- Migration file naming: **`V{NNN}__{descripcion_snake}.sql`** — three
  digits, no gaps. `V001__baseline.sql`, `V002__seed.sql`,
  `V003__seed_insumos.sql`.
- Table/column names: **`snake_case`, singular** (source doc §1). Domain
  terms in Spanish.
- ENUMs: **`VARCHAR(N) + CHECK`**, never Postgres `ENUM` type (source
  doc §1 — avoids `ALTER TYPE` friction).
- Primary keys: **`BIGINT GENERATED ALWAYS AS IDENTITY`** (source doc §1).
- Timestamps: **`TIMESTAMPTZ NOT NULL DEFAULT now()`** on every mutable
  table's `created_at` and `updated_at`.
- **DO NOT** add `updated_at` triggers in this plan. The write-through
  motor (plan 005) will update timestamps at the app layer, in the same
  transaction as the value change. Trigger-based auto-update creates
  ordering hazards with write-through totals.

## Steps

### 1 — Confirm baseline

```bash
./mvnw -q -DskipTests package
```

Expected: BUILD SUCCESS. If not: STOP. Plan 001 or 002 has drifted.

Also verify Flyway is currently mid-configured (from plan 001):

```bash
cat src/main/resources/db/migration/V001__baseline.sql
```

Expected: contains the placeholder `SELECT 1;`. If it contains anything
else: STOP and report — a prior plan may have already written schema.

### 2 — Overwrite `V001__baseline.sql`

Replace the placeholder file **completely** with the DDL block below. The
statements are in dependency order (FK targets before FK sources); do not
reorder. Every table, column, type, constraint, and index below is copied
verbatim from `../../thesis-docs/plan/architecture/06-database-schema.md`.

```sql
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
```

### 3 — Create `V002__seed.sql`

Path: `src/main/resources/db/migration/V002__seed.sql`. Contents:

```sql
-- V002__seed.sql
-- Reference catalogs and system defaults.
-- Source: thesis-docs/plan/architecture/06-database-schema.md §5.
-- CAMICON reference values are intentionally OMITTED pending license
-- confirmation (agenda A7).

-- Canonical units (v1.1 §4.1).
INSERT INTO unidad_catalogo (codigo, descripcion) VALUES
  ('m',      'metro'),
  ('m²',     'metro cuadrado'),
  ('m³',     'metro cúbico'),
  ('kg',     'kilogramo'),
  ('u',      'unidad'),
  ('gl',     'global'),
  ('lt',     'litro'),
  ('pto',    'punto'),
  ('m³·km',  'metro cúbico por kilómetro'),
  ('viaje',  'viaje'),
  ('h',      'hora');

-- Singleton system parameters (DM §11 defaults).
INSERT INTO parametros_sistema (
  id,
  porcentaje_herramienta_menor,
  porcentaje_indirecto,
  iva,
  moneda,
  mostrar_secciones_vacias,
  sufijos_seccion_activos,
  mostrar_subtotales_seccion,
  mostrar_subtotales_pie,
  mostrar_nombre_proyecto_header,
  enumerar_apus,
  mensaje_footer,
  modo_codigo_rubro
) VALUES (
  1,
  0.0500,
  NULL,
  0.1500,
  'USD',
  TRUE,
  TRUE,
  TRUE,
  FALSE,
  FALSE,
  FALSE,
  'Este precio no incluye IVA',
  'AUTOGENERADO'
);
```

### 4 — Create `V003__seed_insumos.sql`

**Source data:** `../../thesis-docs/plan/domain/_artifacts/insumos-seed-apus-cetro-medico-tulcan.csv`.
That file has ~94 rows with columns: `codigo, tipo, descripcion, unidad, precio_unitario`.

**Step 4a — read the CSV** (this is a read-only step to confirm shape and row count):

```bash
head -3 ../thesis-docs/plan/domain/_artifacts/insumos-seed-apus-cetro-medico-tulcan.csv
wc -l ../thesis-docs/plan/domain/_artifacts/insumos-seed-apus-cetro-medico-tulcan.csv
```

Expected: one header row + ~93 data rows (source doc says "93 rows"; the CSV
may have 94 lines including header — that's the same fact).

**Step 4b — generate the SQL.**

Path: `src/main/resources/db/migration/V003__seed_insumos.sql`. Structure:

```sql
-- V003__seed_insumos.sql
-- Central IESS input base (§17 #2). 93 insumos from
-- thesis-docs/plan/domain/_artifacts/insumos-seed-apus-cetro-medico-tulcan.csv.

-- One CENTRAL base row.
INSERT INTO base_insumos (nombre, tipo, proyecto_id, archivada) VALUES
  ('Base IESS Cetro Médico Tulcán', 'CENTRAL', NULL, FALSE);

-- Insumos referencing the base created above.
-- All rows via a CTE so we don't hardcode the base_id (which is IDENTITY-generated).
WITH base AS (
  SELECT id FROM base_insumos WHERE nombre = 'Base IESS Cetro Médico Tulcán'
)
INSERT INTO insumo (base_id, codigo, tipo, descripcion, unidad, precio_unitario)
SELECT base.id, v.codigo, v.tipo, v.descripcion, v.unidad, v.precio_unitario
FROM base, (VALUES
  ('<CODIGO_1>', '<TIPO_1>', '<DESCRIPCION_1>', '<UNIDAD_1>', <PRECIO_1>),
  ('<CODIGO_2>', '<TIPO_2>', '<DESCRIPCION_2>', '<UNIDAD_2>', <PRECIO_2>),
  -- … one row per CSV data row
  ('<CODIGO_N>', '<TIPO_N>', '<DESCRIPCION_N>', '<UNIDAD_N>', <PRECIO_N>)
) AS v(codigo, tipo, descripcion, unidad, precio_unitario);
```

**Guidance for filling the VALUES list:**
- Read the CSV. For each row, emit a tuple in the same order:
  `(codigo, tipo, descripcion, unidad, precio_unitario)`.
- **Escape single quotes in descripcion** by doubling them (`O'Brien` → `'O''Brien'`).
- **Tipo values must match the CHECK constraint**: `EQUIPO`, `MANO_OBRA`,
  `MATERIAL`, `TRANSPORTE`. If the CSV uses different casing / labels
  (e.g. `Equipo`, `Mano de obra`), normalize.
- **Unidad** for EQUIPO/MANO_OBRA rows must be `'h'` (CHECK constraint on
  `insumo`). If the CSV says otherwise, STOP and report which row.
- **Precio unitario** must be > 0 (CHECK constraint). If the CSV has a
  zero or negative, STOP and report.
- **Duplicate `codigo` within the same base is forbidden** (UNIQUE
  `(base_id, codigo)`). If duplicates exist in the CSV, STOP and report;
  do not silently dedupe.

If any of the STOP conditions above triggers, do not proceed with V003 —
the source data needs a clean fix (in the CSV, upstream). V001 and V002
can still land; leave V003 out until the data is clean.

### 5 — Verify the migrations apply

Dev Services will spin a Postgres container automatically. Run the app in
dev mode long enough for Flyway to migrate, then shut it down:

```bash
timeout 45 ./mvnw quarkus:dev 2>&1 | tee /tmp/quarkus-dev.log &
DEV_PID=$!
# Wait for Flyway to log completion — up to 45s
for i in $(seq 1 90); do
  if grep -q 'Successfully applied .* migrations to schema' /tmp/quarkus-dev.log 2>/dev/null; then
    echo "Migrations applied."
    break
  fi
  sleep 0.5
done
kill $DEV_PID 2>/dev/null || true
wait $DEV_PID 2>/dev/null || true
```

Expected log line (Flyway signature — exact wording varies by version but
contains "Successfully applied N migrations"):

```
Successfully applied 3 migrations to schema "public"
```

**If migrations fail**, the Quarkus log will contain the Postgres error
verbatim (e.g. `ERROR: syntax error at or near ...`). STOP and report
that error verbatim. Do not silently patch the SQL — the source doc is
authoritative, and a syntax error means the executor made a transcription
mistake.

**If Docker/Podman is not available** and Dev Services cannot spin a
Postgres, mark step 5 as SKIPPED and report the reason. Steps 2–4 are
still verifiable by parsing (step 6).

### 6 — SQL-parse sanity check (fallback if Docker unavailable)

If step 5 was skipped, run a syntactic sanity check via `psql` on a local
Postgres if one exists, OR verify by opening each file and confirming:

- All `CREATE TABLE` statements have matching parentheses (search for
  unbalanced `(` / `)`).
- All string literals are single-quoted.
- Every `REFERENCES` target table appears earlier in `V001`.

There is no lightweight formal parser we should install for this — the
real test is Flyway applying it in CI (after this plan is merged, plan
002's `ci.yml` will exercise the migrations on every push once integration
tests land in plan 004).

## Done criteria

- [ ] `src/main/resources/db/migration/V001__baseline.sql` contains the
  full DDL from step 2 (not the `SELECT 1;` stub).
- [ ] `src/main/resources/db/migration/V002__seed.sql` exists with the
  unit catalog + parametros_sistema seed from step 3.
- [ ] `src/main/resources/db/migration/V003__seed_insumos.sql` exists
  with one CENTRAL base + N insumo rows sourced from the CSV.
- [ ] `./mvnw -q -DskipTests package` → BUILD SUCCESS.
- [ ] If Docker is available: `./mvnw quarkus:dev` boots and Flyway logs
  "Successfully applied 3 migrations". If Docker not available: SKIPPED
  is acceptable.
- [ ] No `@Entity` classes have been added.
  `find src/main/java -name '*.java'` returns empty.

## Test plan

No production tests in this plan. The migration is verified by Flyway
applying successfully (step 5). Once plan 004 lands its first
`@QuarkusTest`, that test class will trigger Flyway in CI on every push —
free ongoing verification.

Future integration tests should NOT create schema manually — they inherit
Flyway's schema via Dev Services + `migrate-at-start: true` (already
configured by plan 001).

## Maintenance note

- **Never edit an applied migration.** If schema needs to change, add
  `V004__...`, `V005__...`, etc. Editing `V001` after it's been applied
  anywhere breaks Flyway's checksum.
- **When adding an entity in a feature plan**: match column names / types
  EXACTLY to what this migration declared. Hibernate is set to
  `generation: validate` — a mismatch (e.g. `String rol` mapped to
  `VARCHAR(12)` in DB) will fail startup with a helpful message. If you
  see that error, fix the entity, not the schema.
- **`log_actividad.detalle` is JSONB**: use Panache's `@JdbcTypeCode` or
  Hypersistence Utils to map it to a Jackson `JsonNode` when the log
  writer entity lands. Do not stringify JSON at the app layer.
- **Super-admin bootstrap** is deferred to plan 004 (or a manual
  post-migration `INSERT`). Not in this plan.

## Escape hatches — STOP conditions

- The current `V001__baseline.sql` in the repo is not the `SELECT 1;`
  placeholder from plan 001 → STOP. Something has already written schema
  and you'd overwrite it.
- Flyway migration fails at step 5 with a Postgres syntax/type error →
  STOP; report the error verbatim.
- The CSV in step 4b has data that violates a CHECK constraint (bad tipo,
  non-`h` unit for EQUIPO/MANO_OBRA, precio ≤ 0, duplicate codigo) →
  STOP; report which row(s).
- Any step wants you to create a `@Entity` class, a Panache repo, a REST
  resource, or write Java at all → STOP. This plan is pure SQL.
