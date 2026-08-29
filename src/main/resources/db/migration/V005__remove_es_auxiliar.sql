-- V005 — Remove esAuxiliar / no-links (N04 §2)
ALTER TABLE apu DROP COLUMN IF EXISTS es_auxiliar CASCADE;
ALTER TABLE apu_detalle DROP COLUMN IF EXISTS apu_auxiliar_id CASCADE;
DROP INDEX IF EXISTS ix_apu_detalle_auxiliar;

-- Relax tarifa/precio checks for template fallback pending rows
ALTER TABLE apu_detalle DROP CONSTRAINT IF EXISTS apu_detalle_tarifa_jornal_check;
ALTER TABLE apu_detalle ADD CONSTRAINT apu_detalle_tarifa_jornal_check CHECK (tarifa_jornal >= 0);
ALTER TABLE apu_detalle DROP CONSTRAINT IF EXISTS apu_detalle_precio_unitario_tarifa_check;
ALTER TABLE apu_detalle ADD CONSTRAINT apu_detalle_precio_unitario_tarifa_check CHECK (precio_unitario_tarifa >= 0);

-- Drop the mutual-exclusion check (insumo vs auxiliar) if it still exists
ALTER TABLE apu_detalle DROP CONSTRAINT IF EXISTS apu_detalle_check;
ALTER TABLE apu_detalle DROP CONSTRAINT IF EXISTS apu_detalle_check1;
ALTER TABLE apu_detalle DROP CONSTRAINT IF EXISTS apu_detalle_hm_check;

-- Re-add the HM-only check without apu_auxiliar_id
ALTER TABLE apu_detalle ADD CONSTRAINT apu_detalle_hm_check
  CHECK (NOT es_herramienta_menor OR (insumo_id IS NULL
         AND tarifa_jornal IS NULL AND rendimiento IS NULL AND cantidad IS NULL));
