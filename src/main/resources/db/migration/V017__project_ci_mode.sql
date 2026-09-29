ALTER TABLE parametros_proyecto
    ADD COLUMN ci_individual_habilitado BOOLEAN NOT NULL DEFAULT FALSE;

-- Materialize settings for any legacy project with overrides but no parameter row.
INSERT INTO parametros_proyecto (
    proyecto_id, porcentaje_herramienta_menor, porcentaje_indirecto, iva, moneda,
    mostrar_secciones_vacias, sufijos_seccion_activos, mostrar_subtotales_seccion,
    mostrar_subtotales_pie, mostrar_nombre_proyecto_header, enumerar_apus,
    mensaje_footer, modo_codigo_rubro
)
SELECT DISTINCT p.proyecto_id, s.porcentaje_herramienta_menor, s.porcentaje_indirecto, s.iva, s.moneda,
    s.mostrar_secciones_vacias, s.sufijos_seccion_activos, s.mostrar_subtotales_seccion,
    s.mostrar_subtotales_pie, s.mostrar_nombre_proyecto_header, s.enumerar_apus,
    s.mensaje_footer, s.modo_codigo_rubro
FROM presupuesto p
JOIN apu a ON a.presupuesto_id = p.id
CROSS JOIN parametros_sistema s
WHERE s.id = 1
  AND a.porcentaje_indirecto IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM parametros_proyecto pp WHERE pp.proyecto_id = p.proyecto_id
  );

-- Existing explicit APU CI values opt their project into individual CI mode.
-- Preserve every APU override; the project rate and override values are untouched.
UPDATE parametros_proyecto pp
SET ci_individual_habilitado = TRUE
WHERE EXISTS (
    SELECT 1
    FROM presupuesto p
    JOIN apu a ON a.presupuesto_id = p.id
    WHERE p.proyecto_id = pp.proyecto_id
      AND a.porcentaje_indirecto IS NOT NULL
);
