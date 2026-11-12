-- V005__allow_zero_pending_apu_detail_prices.sql
-- Plan 04 (P-26) — Plantillas APU con carga y fallback (dossier 07 §B.4).
-- Relaja los CHECKs de las columnas monetarias de `apu_detalle` que reciben
-- el "override explícito = 0" de las filas pendientes (insumo faltante en
-- PROYECTO + CENTRAL/PERSONAL). El motor sigue operando con `BigDecimal`
-- natural (Plan 014 T1); el cambio es puramente estructural y no afecta
-- V001/V002/V003/V004 ni `insumo.precio_unitario` (los precios de catálogo
-- siguen siendo > 0 — N04 §A9 los garantiza al copiarse de CENTRAL/PERSONAL
-- o ser creados manualmente).
--
-- Antes:
--   tarifa_jornal          NUMERIC(14,6) CHECK (tarifa_jornal > 0)
--   precio_unitario_tarifa NUMERIC(14,6) CHECK (precio_unitario_tarifa > 0)
-- Después:
--   tarifa_jornal          NUMERIC(14,6) CHECK (tarifa_jornal >= 0)
--   precio_unitario_tarifa NUMERIC(14,6) CHECK (precio_unitario_tarifa >= 0)
--
-- Solo afecta a filas que reciben un override "pendiente" desde la carga de
-- plantilla (motor.overridePrecio = 0 sin insumo resuelto). El resto de las
-- invariantes de negocio (cantidad > 0, rendimiento > 0 cuando aplica, etc.)
-- siguen activas.

ALTER TABLE apu_detalle DROP CONSTRAINT IF EXISTS apu_detalle_tarifa_jornal_check;
ALTER TABLE apu_detalle ADD CONSTRAINT apu_detalle_tarifa_jornal_check CHECK (tarifa_jornal IS NULL OR tarifa_jornal >= 0);

ALTER TABLE apu_detalle DROP CONSTRAINT IF EXISTS apu_detalle_precio_unitario_tarifa_check;
ALTER TABLE apu_detalle ADD CONSTRAINT apu_detalle_precio_unitario_tarifa_check CHECK (precio_unitario_tarifa IS NULL OR precio_unitario_tarifa >= 0);