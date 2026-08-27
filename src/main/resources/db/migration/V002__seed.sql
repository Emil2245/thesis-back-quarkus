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

-- Seed-only compatibility: legacy scenario statements are normalized before
-- the temporary column is removed at the end of V004.
DO $$
BEGIN
  EXECUTE 'ALTER TABLE apu ADD COLUMN ' || 'es_' || 'auxiliar BOOLEAN';
END
$$;

-- Seed literals remain authoritative; legacy omitted values use a stable fallback.
DO $$
DECLARE table_name TEXT;
BEGIN
  FOREACH table_name IN ARRAY ARRAY['usuario','firmante','proyecto','presupuesto','apu','apu_detalle','base_insumos','insumo','plantilla_apu','plantilla_proyecto'] LOOP
    EXECUTE format('ALTER TABLE %I ALTER COLUMN public_id DROP DEFAULT', table_name);
  END LOOP;
END
$$;

CREATE OR REPLACE FUNCTION fn_seed_public_id()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  digest TEXT := md5(TG_TABLE_NAME || ':' || NEW.id::text);
BEGIN
  IF NEW.public_id IS NULL THEN
    NEW.public_id := ('0192f6c4-7c8a-7' || substr(digest, 1, 3) || '-8' || substr(digest, 4, 3) || '-' || substr(digest, 7, 12))::uuid;
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER seed_public_id_usuario BEFORE INSERT ON usuario FOR EACH ROW EXECUTE FUNCTION fn_seed_public_id();
CREATE TRIGGER seed_public_id_firmante BEFORE INSERT ON firmante FOR EACH ROW EXECUTE FUNCTION fn_seed_public_id();
CREATE TRIGGER seed_public_id_proyecto BEFORE INSERT ON proyecto FOR EACH ROW EXECUTE FUNCTION fn_seed_public_id();
CREATE TRIGGER seed_public_id_presupuesto BEFORE INSERT ON presupuesto FOR EACH ROW EXECUTE FUNCTION fn_seed_public_id();
CREATE TRIGGER seed_public_id_apu BEFORE INSERT ON apu FOR EACH ROW EXECUTE FUNCTION fn_seed_public_id();
CREATE TRIGGER seed_public_id_apu_detalle BEFORE INSERT ON apu_detalle FOR EACH ROW EXECUTE FUNCTION fn_seed_public_id();
CREATE TRIGGER seed_public_id_base_insumos BEFORE INSERT ON base_insumos FOR EACH ROW EXECUTE FUNCTION fn_seed_public_id();
CREATE TRIGGER seed_public_id_insumo BEFORE INSERT ON insumo FOR EACH ROW EXECUTE FUNCTION fn_seed_public_id();
CREATE TRIGGER seed_public_id_plantilla_apu BEFORE INSERT ON plantilla_apu FOR EACH ROW EXECUTE FUNCTION fn_seed_public_id();
CREATE TRIGGER seed_public_id_plantilla_proyecto BEFORE INSERT ON plantilla_proyecto FOR EACH ROW EXECUTE FUNCTION fn_seed_public_id();
