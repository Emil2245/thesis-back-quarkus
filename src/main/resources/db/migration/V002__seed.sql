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
