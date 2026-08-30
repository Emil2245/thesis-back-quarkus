-- Allow PERSONAL type in base_insumos
ALTER TABLE base_insumos DROP CONSTRAINT IF EXISTS base_insumos_tipo_check;
ALTER TABLE base_insumos ADD CONSTRAINT base_insumos_tipo_check
  CHECK (tipo IN ('CENTRAL', 'PROYECTO', 'PERSONAL'));
-- Add usuario_id for PERSONAL bases
ALTER TABLE base_insumos ADD COLUMN usuario_id BIGINT REFERENCES usuario(id) ON DELETE CASCADE;
-- Update the cross-check
ALTER TABLE base_insumos DROP CONSTRAINT IF EXISTS base_insumos_check;
ALTER TABLE base_insumos ADD CONSTRAINT base_insumos_check
  CHECK (
    (tipo = 'CENTRAL'  AND proyecto_id IS NULL AND usuario_id IS NULL) OR
    (tipo = 'PROYECTO' AND proyecto_id IS NOT NULL) OR
    (tipo = 'PERSONAL' AND proyecto_id IS NULL AND usuario_id IS NOT NULL)
  );
CREATE INDEX ix_base_insumos_usuario ON base_insumos(usuario_id);
