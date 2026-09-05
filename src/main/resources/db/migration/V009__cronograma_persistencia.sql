-- V009__cronograma_persistencia.sql
-- Plan 027 (I-08 / 06-cronograma) — Identidad pública UUIDv7, fingerprint
-- revisado y CHECK canónico de períodos para `cronograma` y `actividad`.
-- Sigue el patrón WU-03 ya establecido para `presupuesto`, `apu`,
-- `apu_detalle`, `plantilla_apu`, `plantilla_proyecto`, `base_insumos`,
-- `insumo` (ver `V001__baseline.sql` §1 y §5) y `capitulo`/`rubro`
-- (ver `V008__capitulo_rubro_public_id.sql`): columna `public_id UUID NOT
-- NULL UNIQUE DEFAULT uuidv7()` + trigger genérico `BEFORE UPDATE OF
-- public_id` reutilizando `fn_assert_public_id_immutable()` declarada en V001.
--
-- Esta migración es **estructural**: no reseed, no backfill explícito. El
-- DEFAULT `uuidv7()` se evalúa por fila durante el ALTER TABLE para los
-- registros existentes. El ALTER TABLE recorre toda la tabla al materializar
-- el DEFAULT — **no** es O(1) — pero el volumen actual lo admite.
--
-- Cambios aditivos (sin tocar V001..V008):
--   - `cronograma.public_id` UUIDv7 NOT NULL UNIQUE
--   - `actividad.public_id` UUIDv7 NOT NULL UNIQUE
--   - `cronograma.presupuesto_fingerprint_revisado CHAR(64) NULL` + CHECK
--     lowercase SHA-256 anclado (CHAR padding garantiza que longitudes
--     distintas de 64 NO satisfacen la expresión regular `^[0-9a-f]{64}$`).
--   - Reemplazo del CHECK histórico `cronograma.numero_periodos > 0` por el
--     límite canónico dependiente de la unidad (SEMANA 1..520 / MES 1..120).
--   - Triggers `trg_public_id_immutable` por tabla, reutilizando
--     `fn_assert_public_id_immutable()` de V001 §5. No se crea una función
--     por tabla.
--
-- Invariantes preservadas (no se alteran):
--   - PK/FK internas siguen BIGINT.
--   - `UNIQUE (cronograma.presupuesto_id)` y `UNIQUE (actividad.rubro_id)`
--     (1:1) — son la autoridad, no se añade una segunda constraint.
--   - `cronograma_actividad` (seam histórico inerte) no se toca ni se usa.
--   - `avance_por_periodo` y demás JSONB permanecen semánticamente intactos.
--
-- Antes:
--   cronograma: BIGINT PK + FKs, sin columna de identidad externa,
--               CHECK (numero_periodos > 0)
--   actividad:  BIGINT PK + FKs, sin columna de identidad externa
-- Después:
--   cronograma: añade public_id UUID NOT NULL UNIQUE DEFAULT uuidv7() +
--               presupuesto_fingerprint_revisado CHAR(64) NULL +
--               CHECK (fingerprint NULL | ~ '^[0-9a-f]{64}$') +
--               CHECK ((SEMANA 1..520) | (MES 1..120)) + trigger
--   actividad:  añade public_id UUID NOT NULL UNIQUE DEFAULT uuidv7() + trigger

-- ──────────────────────────────────────────────────────────────────────
-- 1) Public identity columns
-- ──────────────────────────────────────────────────────────────────────

ALTER TABLE cronograma
    ADD COLUMN public_id UUID NOT NULL UNIQUE DEFAULT uuidv7();

ALTER TABLE actividad
    ADD COLUMN public_id UUID NOT NULL UNIQUE DEFAULT uuidv7();

-- ──────────────────────────────────────────────────────────────────────
-- 2) Fingerprint column + lowercase SHA-256 CHECK
-- ──────────────────────────────────────────────────────────────────────

ALTER TABLE cronograma
    ADD COLUMN presupuesto_fingerprint_revisado CHAR(64);

-- La columna es CHAR(64): una cadena de longitud distinta de 64 queda
-- rellenada con espacios al persistir, lo que rompe la regex anclada.
-- Esta restricción rechaza a la vez valores cortos, largos, con mayúsculas
-- o con caracteres no-hexadecimales. La semántica "lowercase exact" se
-- cumple bit-a-bit cuando la entrada tiene 64 caracteres.
ALTER TABLE cronograma
    ADD CONSTRAINT chk_cronograma_fingerprint
        CHECK (presupuesto_fingerprint_revisado IS NULL
               OR presupuesto_fingerprint_revisado ~ '^[0-9a-f]{64}$');

-- ──────────────────────────────────────────────────────────────────────
-- 3) Reemplazo del CHECK de `numero_periodos`
-- ──────────────────────────────────────────────────────────────────────

-- El CHECK histórico `numero_periodos > 0` lleva el nombre generado por
-- Postgres para el CHECK inline del DDL. V004 (seed IESS) inserta filas
-- con SEMANA y MES dentro del rango 1..520 / 1..120, por lo que el
-- reemplazo no viola datos existentes.
ALTER TABLE cronograma
    DROP CONSTRAINT cronograma_numero_periodos_check;

ALTER TABLE cronograma
    ADD CONSTRAINT chk_cronograma_periodos_unidad
        CHECK ((unidad_tiempo = 'SEMANA' AND numero_periodos BETWEEN 1 AND 520)
               OR (unidad_tiempo = 'MES'    AND numero_periodos BETWEEN 1 AND 120));

-- ──────────────────────────────────────────────────────────────────────
-- 4) Triggers de inmutabilidad (reutilizan la función de V001)
-- ──────────────────────────────────────────────────────────────────────

CREATE TRIGGER trg_public_id_immutable
    BEFORE UPDATE OF public_id ON cronograma
    FOR EACH ROW EXECUTE FUNCTION fn_assert_public_id_immutable();

CREATE TRIGGER trg_public_id_immutable
    BEFORE UPDATE OF public_id ON actividad
    FOR EACH ROW EXECUTE FUNCTION fn_assert_public_id_immutable();