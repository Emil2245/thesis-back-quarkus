-- V008__capitulo_rubro_public_id.sql
-- Plan 019 (I-07 / 05-presupuesto) — Identidad pública UUIDv7 para `capitulo`
-- y `rubro` (módulo `presupuesto`). Sigue el patrón WU-03 ya establecido para
-- `presupuesto`, `apu`, `apu_detalle`, `plantilla_apu`, `plantilla_proyecto`,
-- `base_insumos` e `insumo` (ver `V001__baseline.sql` §1 y §5): columna
-- `public_id UUID NOT NULL UNIQUE DEFAULT uuidv7()` + trigger genérico
-- `BEFORE UPDATE OF public_id` reutilizando `fn_assert_public_id_immutable()`
-- declarada en V001.
--
-- Esta migración es **estructural**: no reseed, no backfill explícito. El
-- DEFAULT `uuidv7()` se evalúa por fila durante el ALTER TABLE, así que las
-- filas que V001–V007 dejaron en `capitulo` y `rubro` (incluidas las del seed
-- V004) reciben un UUIDv7 sin lógica adicional. El ALTER TABLE recorre toda
-- la tabla al materializar el DEFAULT para los registros existentes — **no**
-- es O(1) — pero el volumen actual lo admite; el trigger de inmutabilidad no
-- depende del orden de generación. El DEFAULT es volátil y Postgres lo evalúa
-- en el momento del INSERT; no es un valor "inmutable" del lado lógico, sólo
-- del lado de la capa de inmutabilidad activada por el trigger.
--
-- Antes:
--   capitulo: BIGINT PK + FKs, sin columna de identidad externa
--   rubro:    BIGINT PK + FKs, sin columna de identidad externa
-- Después:
--   capitulo: añade public_id UUID NOT NULL UNIQUE DEFAULT uuidv7() + trigger
--   rubro:    añade public_id UUID NOT NULL UNIQUE DEFAULT uuidv7() + trigger
--
-- PK/FK internas siguen BIGINT (misma invariante que el resto de tablas
-- públicas). El FK `rubro.apu_id → apu.id` (UNIQUE por D-09) no se toca.

ALTER TABLE capitulo
    ADD COLUMN public_id UUID NOT NULL UNIQUE DEFAULT uuidv7();

ALTER TABLE rubro
    ADD COLUMN public_id UUID NOT NULL UNIQUE DEFAULT uuidv7();

-- Trigger de inmutabilidad por tabla, reutilizando la función genérica
-- `fn_assert_public_id_immutable()` declarada en V001. No se crean funciones
-- por tabla; el patrón vigente es compartir una sola función entre todas las
-- tablas públicas.
CREATE TRIGGER trg_public_id_immutable
    BEFORE UPDATE OF public_id ON capitulo
    FOR EACH ROW EXECUTE FUNCTION fn_assert_public_id_immutable();

CREATE TRIGGER trg_public_id_immutable
    BEFORE UPDATE OF public_id ON rubro
    FOR EACH ROW EXECUTE FUNCTION fn_assert_public_id_immutable();
