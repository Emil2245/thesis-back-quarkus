-- V007__rubro_cantidad_zero_pending_allowed.sql
-- Plan 06 (P-46, N04 §A8) — Plantillas de proyecto completas.
-- Relaja la CHECK de `rubro.cantidad` para permitir `cantidad = 0.000000`
-- en los rubros reconstruidos al aplicar una plantilla (Plan 06 §4 — el
-- usuario completa las cantidades de obra después). La columna sigue siendo
-- `NOT NULL` (no se hace nullable); sólo se amplía el dominio válido de
-- cero a positivo.
--
-- Antes:
--   cantidad NUMERIC(12,6) NOT NULL CHECK (cantidad > 0)
-- Después:
--   cantidad NUMERIC(12,6) NOT NULL CHECK (cantidad >= 0)
--
-- Sólo afecta la fila de la tabla; no toca la API (la API exige > 0 vía
-- contratos P-29 / RNF-09 — este cambio es estrictamente interno para que
-- la reconstrucción de plantillas pueda sembrar pendientes). El usuario
-- edita las cantidades de obra resultantes a un valor > 0 antes de operar.

ALTER TABLE rubro DROP CONSTRAINT IF EXISTS rubro_cantidad_check;
ALTER TABLE rubro ADD CONSTRAINT rubro_cantidad_check CHECK (cantidad >= 0);